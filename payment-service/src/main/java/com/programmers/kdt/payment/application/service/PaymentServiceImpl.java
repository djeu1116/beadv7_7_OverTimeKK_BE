package com.programmers.kdt.payment.application.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.contract.PaymentFailEvent;
import com.programmers.kdt.payment.infrastructure.client.pg.*;
import com.programmers.kdt.common.contract.CompensationCompletedEvent;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskType;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskWriter;
import com.programmers.kdt.common.contract.OrderCancelRequestedEvent;
import com.programmers.kdt.common.contract.RefundCompletedEvent;
import com.programmers.kdt.common.contract.RefundFailedEvent;
import com.programmers.kdt.payment.infrastructure.client.order.OrderClient;
import com.programmers.kdt.payment.infrastructure.client.order.OrderInfo;
import com.programmers.kdt.payment.infrastructure.client.order.StartPaymentOutcome;
import com.programmers.kdt.payment.infrastructure.client.refund.*;
import com.programmers.kdt.payment.presentation.dto.*;
import com.programmers.kdt.payment.domain.entity.Payment;
import com.programmers.kdt.payment.domain.entity.PaymentAttempt;
import com.programmers.kdt.payment.domain.entity.PaymentRefund;
import com.programmers.kdt.payment.domain.entity.PaymentStatus;
import com.programmers.kdt.payment.domain.entity.RefundPolicy;
import com.programmers.kdt.payment.domain.entity.outbox.OutboxEventType;
import com.programmers.kdt.payment.domain.exception.PaymentErrorCode;
import com.programmers.kdt.payment.domain.exception.PointErrorCode;
import com.programmers.kdt.payment.infrastructure.repository.PaymentAttemptRepository;
import com.programmers.kdt.payment.infrastructure.repository.PaymentRefundRepository;
import com.programmers.kdt.payment.infrastructure.repository.PaymentRepository;
import com.programmers.kdt.payment.application.service.tx.PaymentTxOps;
import com.programmers.kdt.payment.application.service.tx.PgOutcome;
import com.programmers.kdt.payment.application.service.util.PointEventIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;


@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService{

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PerformanceClient performanceClient;
    private final OrderClient orderClient;
    private final PgClient pgClient;
    private final PointService pointService;
    private final IdempotencyKeyService idempotencyKeyService;
    private final ObjectMapper objectMapper;
    private final PaymentTxOps paymentTxOps;
    private final OutboxEventWriter outboxEventWriter;
    private final ReconciliationTaskWriter reconciliationTaskWriter;


    @Transactional
    public CreatePaymentResponse pay(String idempotencyKey, CreatePaymentRequest request, Long userId) {
        // userId로 스코프해서 다른 사용자가 같은 idempotencyKey 문자열을 쓰더라도(추측/재사용) 서로 다른
        // 키로 취급한다 - 안 그러면 캐시 hit 시 소유권 검증(doPay/doConfirm 안)을 아예 안 거치고
        // 다른 사용자의 캐시된 응답을 그대로 돌려줄 수 있음(L-1).
        String key = "PAY:" + userId + ":" + idempotencyKey;
        String requestHash = hashRequest(request);
        Optional<String> cached = idempotencyKeyService.generate(key, requestHash);
        if (cached.isPresent()) {
            return deserialize(cached.get(), CreatePaymentResponse.class);
        }

        try {
            CreatePaymentResponse response = doPay(request, userId);
            idempotencyKeyService.complete(key, toJson(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyKeyService.release(key);
            throw e;
        }
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private <T> T deserialize(String json, Class<T> type) {
        return objectMapper.readValue(json, type);
    }

    private String hashRequest(Object request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(toJson(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // 결제 생성
    private CreatePaymentResponse doPay(CreatePaymentRequest request, Long userId) {
        OrderInfo order = orderClient.findOrder(request.orderId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND));

        if (!order.userId().equals(userId)) throw new BusinessException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);

        Optional<Payment> existing = paymentRepository.findByOrderId(request.orderId());
        if (existing.isPresent() && existing.get().getPaymentStatus() != PaymentStatus.FAILED) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_EXISTS);
        }

        StartPaymentOutcome outcome = orderClient.startPayment(request.orderId());
        switch (outcome) {
            case EXPIRED -> throw new BusinessException(PaymentErrorCode.ORDER_ALREADY_EXPIRED);
            case NOT_PENDING -> throw new BusinessException(PaymentErrorCode.ORDER_NOT_PENDING);
            case NOT_FOUND -> throw new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND);
            case STARTED -> { }
        }

        // 여기부터는 주문이 이미 PAYMENT_STARTED로 전이된 뒤라, 실패하면 그 전이를 되돌려야 한다.
        // 주문과 결제가 서로 다른 트랜잭션이면 이 메서드가 롤백돼도 주문 상태는 남기 때문.
        try {
            return readyPayment(request, order, existing);
        } catch (RuntimeException e) {
            compensatePaymentStart(request.orderId());
            throw e;
        }
    }

    // 주문 전이를 되돌리는 보상. 이 호출마저 실패하면 주문이 PAYMENT_STARTED로 남으므로 대사 대상으로 남긴다.
    private void compensatePaymentStart(Long orderId) {
        try {
            orderClient.cancelPaymentStart(orderId);
        } catch (Exception e) {
            log.error("[PAYMENT_START_RECONCILIATION_NEEDED] 결제 시작 보상 실패 - orderId={}", orderId, e);
            reconciliationTaskWriter.record(ReconciliationTaskType.PAYMENT_START_COMPENSATION_FAILED, orderId, e.getMessage());
        }
    }

    private CreatePaymentResponse readyPayment(CreatePaymentRequest request, OrderInfo order, Optional<Payment> existing) {
        // 주문 금액이 같은지 판별
        if (!order.totalAmount().equals(request.amount())) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_AMOUNT);
        }

        // 사용 포인트 검증 및 차감 (PG 결제 금액 = 주문 금액 - 사용 포인트)
        Long usedPoint = request.usedPointOrZero();
        if (usedPoint >= request.amount() || usedPoint < 0) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_AMOUNT);
        }

        // 아직 포인트 전액 결제는 고려하지 않은 상태.
        Long pgAmount = request.amount() - usedPoint;

        // 재시도해도 이전 시도와 다른 eventId가 나오도록 attemptSeq를 미리 확정해서 포인트/Payment에 동일하게 반영
        int attemptSeq = existing.map(p -> p.getAttemptSeq() + 1).orElse(0);

        if (usedPoint > 0) {
            pointService.usePoint(order.userId(), usedPoint, PointEventIds.useEventId(request.orderId(), attemptSeq));
        }

        PgReadyResult readyResult = callPg("토스 결제 준비", request.orderId(),
                () -> pgClient.ready(new PgReadyCommand(request.orderId(), pgAmount)));
        Payment payment;
        if (existing.isPresent()) {
            payment = existing.get();
            payment.retryReady(readyResult.orderId(), attemptSeq);
        } else {
            payment = Payment.create(order.orderId(), order.userId(), request.amount());
            payment.assignPgOrderId(readyResult.orderId());
        }
        paymentRepository.save(payment);
        // 이 시도의 이력을 별도 행으로 남긴다 - Payment 자체는 다음 재시도 때 이 값들을 덮어쓰지만
        // 이 행은 attemptSeq로 고정돼 있어 안 건드려진다.
        paymentAttemptRepository.save(PaymentAttempt.create(payment.getId(), attemptSeq, readyResult.orderId(), usedPoint));

        return CreatePaymentResponse.of(payment, readyResult);
    }

    public ConfirmPaymentResponse confirm(Long paymentId, ConfirmPaymentRequest request, String idempotencyKey, Long userId) {
        // pay()와 같은 이유(L-1) - userId로 스코프해서 캐시 hit가 곧 "내 캐시"임을 보장한다.
        String key = "CONFIRM:" + userId + ":" + idempotencyKey;
        String requestHash = hashRequest(request);
        Map<String, Long> timings = new LinkedHashMap<>();
        long start = System.nanoTime();

        Optional<String> cached = time(timings, "idemGenerate", () -> idempotencyKeyService.generate(key, requestHash));
        if (cached.isPresent()) {
            logConfirmTiming(paymentId, timings, start, "CACHE_HIT");
            return deserialize(cached.get(), ConfirmPaymentResponse.class);
        }

        try {
            ConfirmPaymentResponse response = doConfirm(paymentId, request, userId, timings);
            time(timings, "idemComplete", () -> { idempotencyKeyService.complete(key, toJson(response)); return null; });
            logConfirmTiming(paymentId, timings, start, "OK");
            return response;
        } catch (RuntimeException e) {
            idempotencyKeyService.release(key);
            logConfirmTiming(paymentId, timings, start, "ERROR:" + e.getClass().getSimpleName());
            throw e;
        }
    }

    // TODO(perf): confirm() tx 분리 리팩토링 조사용 임시 계측. 측정 끝나면 제거.
    private <T> T time(Map<String, Long> timings, String label, Supplier<T> action) {
        long t0 = System.nanoTime();
        try {
            return action.get();
        } finally {
            timings.put(label, (System.nanoTime() - t0) / 1_000_000);
        }
    }

    private void logConfirmTiming(Long paymentId, Map<String, Long> timings, long start, String result) {
        long totalMs = (System.nanoTime() - start) / 1_000_000;
        log.info("[CONFIRM_TIMING] paymentId={} result={} segments={} totalMs={}", paymentId, result, timings, totalMs);
    }

    // 결제 확인
    private ConfirmPaymentResponse doConfirm(Long paymentId, ConfirmPaymentRequest request, Long userId, Map<String, Long> timings) {
        // 소유권 검증은 assignKeyAndCommit(tx1) 안에서, 상태 변경·커밋보다 먼저 수행됨
        PaymentTxOps.ReadyPaymentContext readyContext = time(timings, "tx1_assignKeyAndFindPoint",
                () -> paymentTxOps.assignKeyAndCommit(paymentId, request.transactionKey(), userId)); // tx1 (+ 포인트 조회 병합)
        Payment payment = readyContext.payment();
        Long usedPoint = readyContext.usedPoint();

        Long pgApproveAmount = payment.getAmount() - usedPoint;

        PgOutcome outcome;
        PgApproveResult approveResult = null;

        // PG
        long pgStart = System.nanoTime();
        try {
            approveResult = pgClient.approve(new PgApproveCommand(payment.getPaymentKey(), payment.getPgOrderId(), pgApproveAmount));
            outcome = approveResult.success() ? PgOutcome.SUCCESS : PgOutcome.EXPLICIT_FAIL;
        } catch (PgClientException e) {
            log.error("토스 결제 승인 거절 - paymentId={}, pgCode={}", paymentId, e.getPgErrorCode(), e);
            outcome = PgOutcome.EXPLICIT_FAIL;
        } catch (RestClientException e) {
            log.error("토스 결제 승인 실패(응답 없음) - paymentId={}", paymentId, e);
            outcome = PgOutcome.AMBIGUOUS;
        } finally {
            timings.put("pgApprove", (System.nanoTime() - pgStart) / 1_000_000);
        }

        PgOutcome finalOutcome = outcome;
        payment = time(timings, "tx2_applyConfirmResult",
                () -> paymentTxOps.applyConfirmResult(paymentId, finalOutcome)); // tx2

        // PAYMENT_CONFIRMED/PAYMENT_FAILED 이벤트는 tx2(applyConfirmResult) 안에서 이미 outbox에 기록됨 - 여기서 직접 발행하지 않음
        Payment finalPayment = payment;
        Long finalUsedPoint = usedPoint;
        time(timings, "postProcess", () -> {
            switch (finalOutcome) {
                case SUCCESS -> { }
                case EXPLICIT_FAIL -> rollbackFailedPoint(finalPayment, finalUsedPoint);
                case AMBIGUOUS -> paymentTxOps.applyReconcileResult(finalPayment.getId(), finalOutcome);
            }
            return null;
        });
        return ConfirmPaymentResponse.from(payment);
    }

    private Long resolveUsedPoint(String eventId) {
        Long usedPoint = pointService.findUsedAmount(eventId);
        return usedPoint == null ? 0L : usedPoint;
    }

    // 결제 실패 요청
    @Transactional
    public FailPaymentResponse fail(Long paymentId, FailPaymentRequest request, Long userId) {
        Payment payment = getPayment(paymentId);

        if (!payment.getUserId().equals(userId)) throw new BusinessException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);

        boolean alreadyFailed = payment.getPaymentStatus() == PaymentStatus.FAILED;
        payment.fail();

        try {
            paymentRepository.saveAndFlush(payment); // PG 요청전, 이중 호출 방지
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONCURRENT_MODIFICATION);
        }

        if (!alreadyFailed) {
            if (payment.getPaymentKey() != null) {
                callPg("토스 결제 취소", paymentId,
                        () -> pgClient.cancel(new PgCancelCommand(payment.getPaymentKey(), payment.getAmount(),
                                request.reason(), PgIdempotencyKeys.cancelKey(payment.getId()))));
            }

            paymentAttemptRepository.findByPaymentIdAndAttemptSeq(payment.getId(), payment.getAttemptSeq())
                    .ifPresent(attempt -> {
                        attempt.markFailed();
                        paymentAttemptRepository.save(attempt);
                    });

            Long usedPoint = getUsedPointForOrder(payment);
            rollbackFailedPoint(payment, usedPoint);
            outboxEventWriter.enqueue(OutboxEventType.PAYMENT_FAILED, payment.getId(),
                    new PaymentFailEvent(payment.getOrderId(), payment.getId(), request.reason()));
        }

        return FailPaymentResponse.from(payment);
    }

    // 결제 내역 조회
    @Transactional(readOnly = true)
    public Page<GetPaymentHistoryResponse> getPaymentHistory(Long userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable)
                .map(GetPaymentHistoryResponse::from);
    }

    // 주문 취소 접수를 받아 환불을 접수한다. 주문이 직접 호출하지 않고 주문 outbox 릴레이가 발행한다.
    // 접수 거부(환불 기간 만료 등)도 예외가 아니라 실패 이벤트로 알려서 주문이 취소 접수를 되돌리게 한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderCancelRequested(OrderCancelRequestedEvent event) {
        Payment payment = paymentRepository.findByOrderId(event.orderId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        // 이미 환불이 진행 중이거나 끝났으면 그 흐름이 주문 상태까지 정리한다 - 중복 수신 대비
        if (payment.getPaymentStatus() == PaymentStatus.REFUND_PENDING
                || payment.getPaymentStatus() == PaymentStatus.CANCELLED) {
            log.info("이미 환불이 진행 중이거나 완료된 결제 - paymentId={}, status={}", payment.getId(), payment.getPaymentStatus());
            return;
        }

        if (payment.getPaymentStatus() != PaymentStatus.PAID) {
            finishRefundFailed(payment, PaymentErrorCode.INVALID_PAYMENT_STATUS.toString());
            return;
        }

        // 환불 가능 기간이 지났으면 접수 거부
        Long ticketId = orderClient.getTicketId(event.orderId());
        LocalDate performanceDate = performanceClient.getPerformanceDate(ticketId);
        double refundRate = RefundPolicy.resolveRefundRate(performanceDate, LocalDate.now());
        if (refundRate == 0.0) {
            outboxEventWriter.enqueue(OutboxEventType.REFUND_FAILED, payment.getId(),
                    new RefundFailedEvent(payment.getOrderId(), payment.getId(),
                            PaymentErrorCode.REFUND_PERIOD_EXPIRED.toString()));
            return;
        }

        // 낙관락 충돌은 예외를 그대로 올려서 릴레이가 재시도하게 둔다
        payment.requestRefund();
        paymentRepository.saveAndFlush(payment);

        outboxEventWriter.enqueue(OutboxEventType.REFUND_REQUESTED, payment.getId(),
                new RefundRequestEvent(payment.getId(), event.reason(), LocalDateTime.now()));
    }

    // 환불 처리(PG사 호출) - outbox relay가 REFUND_REQUESTED를 재발행해서 호출함(동기 실행이어야
    // relay가 성공/실패를 알 수 있음 - @Async를 쓰면 relay가 결과를 못 보고 재시도 여부를 못 정함)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW) // 기존 트랜잭션을 보류시키고 새로운 트랜잭션을 생성
    public void onRefundRequested(RefundRequestEvent event) {
        Payment payment = getPayment(event.paymentId());

        try {
            Long ticketId = orderClient.getTicketId(payment.getOrderId());
            LocalDate performanceDate = performanceClient.getPerformanceDate(ticketId);
            double refundRate = RefundPolicy.resolveRefundRate(performanceDate, LocalDate.now());

            if (refundRate == 0.0) {
                finishRefundFailed(payment, PaymentErrorCode.REFUND_PERIOD_EXPIRED.toString());
                return;
            }
            if (executeRefund(payment, event.reason(), refundRate)) {
                outboxEventWriter.enqueue(OutboxEventType.REFUND_COMPLETED, payment.getId(),
                        new RefundCompletedEvent(payment.getOrderId(), payment.getId()));
            } else {
                finishRefundFailed(payment, PaymentErrorCode.PG_REQUEST_FAILED.toString());
            }
        } catch (Exception e) {
            log.error("환불 처리 중 예외 발생 - paymentID={}", payment.getId());
            finishRefundFailed(payment, PaymentErrorCode.REFUND_SERVICE_FAILED.toString());
        }
    }

    // 결제는 PAID로 확정됐는데 그 다음 단계(티켓 예약 등)가 outbox relay 재시도를 다 소진하도록
    // 계속 실패했을 때 시스템이 스스로 트리거하는 보상. 고객 귀책이 아니므로 환불 정책 요율 계산 없이
    // 전액(100%) 취소한다 - onRefundRequested(RefundRequestEvent)와 이벤트 타입만 다른 오버로드.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRefundRequested(CompensationRequestEvent event) {
        Payment payment = getPayment(event.paymentId());

        if (payment.getPaymentStatus() != PaymentStatus.PAID) {
            // 다른 흐름(고객 환불 등)이 이미 이 결제를 처리 중/처리 완료 - 중복 보상 방지
            log.info("보상 대상이 이미 다른 상태로 처리됨 - paymentId={}, status={}", payment.getId(), payment.getPaymentStatus());
            return;
        }

        try {
            payment.requestRefund();
            if (executeRefund(payment, "TICKET_RESERVATION_FAILED", 1.0)) {
                // 주문은 CANCEL_REQUESTED를 거친 적이 없어서 고객 환불용 REFUND_COMPLETED가 아닌 별도 이벤트로 알림
                outboxEventWriter.enqueue(OutboxEventType.COMPENSATION_COMPLETED, payment.getId(),
                        new CompensationCompletedEvent(payment.getOrderId(), payment.getId()));
            } else {
                revertCompensation(payment, PaymentErrorCode.PG_REQUEST_FAILED.toString());
            }
        } catch (Exception e) {
            log.error("보상 처리 중 예외 발생 - paymentId={}", payment.getId(), e);
            revertCompensation(payment, "COMPENSATION_FAILED");
        }
    }

    // 보상 실패 - 결제는 PAID로 되돌리고 주문 쪽에는 알리지 않음(주문이 CANCEL_REQUESTED가 아니라서 환불 실패 이벤트를 받을 수 없음).
    // 자동 재시도 수단이 없어 로그 마커 기반 수동 대사 대상
    private void revertCompensation(Payment payment, String failReason) {
        payment.failRefund();
        paymentRepository.save(payment);
        log.error("[COMPENSATION_RECONCILIATION_NEEDED] 보상(PG 취소) 실패 - paymentId={}, orderId={}, reason={}",
                payment.getId(), payment.getOrderId(), failReason);
        reconciliationTaskWriter.record(ReconciliationTaskType.COMPENSATION_FAILED, payment.getId(),
                "orderId=" + payment.getOrderId() + ", reason=" + failReason);
    }

    // PG취소 + 환불이력저장 + completeRefund + 포인트롤백 (고객 환불/시스템 보상 공유)
    // PG 취소가 거절되면 아무 것도 바꾸지 않고 false - 결과 알림(outbox)은 호출하는 쪽이 성격에 맞게 기록
    private boolean executeRefund(Payment payment, String reason, double refundRate) {
        Long usedPoint = getUsedPointForOrder(payment);
        Long pgPaidAmount = payment.getAmount() - usedPoint;
        Long refundAmount = RefundPolicy.calculateRefundAmount(pgPaidAmount, refundRate);
        PgCancelResult cancelResult = pgClient.cancel(new PgCancelCommand(payment.getPaymentKey(), refundAmount, reason,
                PgIdempotencyKeys.cancelKey(payment.getId())));

        if (!cancelResult.success()) {
            return false;
        }

        paymentRefundRepository.save(PaymentRefund.create(payment.getId(), refundAmount, reason));
        payment.completeRefund(refundAmount);

        if (usedPoint > 0) {
            try {
                rollbackRefundPointWithRetry(payment, usedPoint, refundRate);
            } catch (Exception e) {
                // 환불/보상은 이미 확정, 포인트 환급이 실패되더라도 그대로 완료 처리
                log.error("[REFUND_POST_PROCESS_FAILED] 환불은 완료됐으나 후처리 실패 - paymentId={}", payment.getId(), e);
                reconciliationTaskWriter.record(ReconciliationTaskType.REFUND_POINT_ROLLBACK_FAILED, payment.getId(),
                        usedPoint, "orderId=" + payment.getOrderId() + ", unexpected=" + e.getMessage());
            }
        }
        return true;
    }

    private void finishRefundFailed(Payment payment, String failReason) {
        payment.failRefund();
        paymentRepository.save(payment);
        log.warn("환불 실패 - paymentId={}, reason={}", payment.getId(), failReason);
        outboxEventWriter.enqueue(OutboxEventType.REFUND_FAILED, payment.getId(),
                new RefundFailedEvent(payment.getOrderId(), payment.getId(), failReason));
    }

    // 환불 내역 조회
    @Transactional(readOnly = true)
    public Page<GetPaymentRefundHistoryResponse> getRefundHistory (Long paymentId, Long userId, Pageable pageable) {
        Payment payment = getPayment(paymentId);

        if (!payment.getUserId().equals(userId)) throw new BusinessException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);

        return paymentRefundRepository.findByPaymentId(paymentId, pageable)
                .map(GetPaymentRefundHistoryResponse::from);
    }

    // PG사 호출
    private <T> T callPg(String action, Object contextId, Supplier<T> pgCall) {
        try {
            return pgCall.get();
        } catch (PgClientException e) {
            log.error("{} 실패 - id={}, pgCode={}, pgMessage={}", action, contextId, e.getPgErrorCode(), e.getMessage(), e);
            throw new BusinessException(PaymentErrorCode.PG_REQUEST_FAILED);
        } catch (Exception e) {
            log.error("PG 요청 중 알 수 없는 오류 - action={}, id={}", action, contextId, e);
            throw new BusinessException(PaymentErrorCode.PG_REQUEST_FAILED);
        }
    }

    private Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        }

        private Long getUsedPointForOrder(Payment payment) {
            return resolveUsedPoint(PointEventIds.useEventId(payment.getOrderId(), payment.getAttemptSeq()));
        }


    // 재시도(동시성 충돌 대비) - 예전엔 재시도/대사 기록 없이 예외가 그대로 새서 결제 상태는 이미
    // 커밋됐는데 사용자에게 500이 갈 수 있었음. PaymentReconciliationScheduler의 같은 로직과 동일 패턴.
    private static final int MAX_POINT_ROLLBACK_ATTEMPTS = 3;

    private void rollbackFailedPoint(Payment payment, Long usedPoint) {
        if (usedPoint <= 0) {
            return;
        }
        Long orderId = payment.getOrderId();
        Long paymentId = payment.getId();
        int attemptSeq = payment.getAttemptSeq();
        String originEventId = PointEventIds.useEventId(orderId, attemptSeq);
        String rollbackEventId = PointEventIds.rollbackFailEventId(orderId, attemptSeq);

        for (int attempt = 1; attempt <= MAX_POINT_ROLLBACK_ATTEMPTS; attempt++) {
            try {
                pointService.rollbackPoint(originEventId, usedPoint, rollbackEventId, true);
                return;
            } catch (BusinessException e) {
                boolean retryable = e.getErrorCode() == PointErrorCode.POINT_CONCURRENT_MODIFICATION;
                if (!retryable || attempt == MAX_POINT_ROLLBACK_ATTEMPTS) {
                    log.error("[POINT_ROLLBACK_RECONCILIATION_NEEDED] 결제는 실패됐지만 포인트 롤백에 실패했습니다. " +
                                    "paymentId={}, orderId={}, amount={}, errorCode={}",
                            paymentId, orderId, usedPoint, e.getErrorCode(), e);
                    reconciliationTaskWriter.record(ReconciliationTaskType.POINT_ROLLBACK_FAILED, paymentId, usedPoint,
                            "orderId=" + orderId + ", errorCode=" + e.getErrorCode());
                    return;
                }
            }
        }
    }

    // 재시도
    private void rollbackRefundPointWithRetry(Payment payment, Long usedPoint, double refundRate) {
        Long refundedPoint = RefundPolicy.calculateRefundAmount(usedPoint, refundRate);
        if (refundedPoint <= 0) {
            return;
        }

        Long orderId = payment.getOrderId();
        Long paymentId = payment.getId();
        int attemptSeq = payment.getAttemptSeq();
        boolean isFullRollback = refundedPoint.equals(usedPoint);
        String originEventId = PointEventIds.useEventId(orderId, attemptSeq);
        String rollbackEventId = PointEventIds.rollbackEventId(orderId, attemptSeq);

        int maxAttempts = 3; // 최대 재시도 횟수
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                pointService.rollbackPoint(originEventId, refundedPoint, rollbackEventId, isFullRollback);
                return;
            } catch (BusinessException e) {
                    boolean retryable = e.getErrorCode() == PointErrorCode.POINT_CONCURRENT_MODIFICATION;
                if (!retryable || attempt == maxAttempts) {
                    // 환불은 이미 완료된 상태이지만 포인트는 수동 보정이 필요한 상태이므로, 로그를 남김
                    // 후에 메시지 큐를 만들어서 배치가 주기적으로 재시도하는 구조로 리팩토링 가능
                    log.error("[POINT_REFUND_RECONCILIATION_NEEDED] 환불은 완료됐지만 포인트 환급에 실패했습니다. " +
                                    "paymentId={}, orderId={}, amount={}, errorCode={}",
                            paymentId, orderId, refundedPoint, e.getErrorCode(), e);
                    reconciliationTaskWriter.record(ReconciliationTaskType.REFUND_POINT_ROLLBACK_FAILED, paymentId,
                            refundedPoint, "orderId=" + orderId + ", errorCode=" + e.getErrorCode());
                    return;
                }
                log.warn("포인트 환급 동시성 충돌, 재시도 {}회차 - orderId={}", attempt, orderId);
            }
        }
    }
}
