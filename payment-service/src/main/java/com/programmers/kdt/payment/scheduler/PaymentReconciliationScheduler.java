package com.programmers.kdt.payment.scheduler;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.client.pg.PgApproveResult;
import com.programmers.kdt.payment.client.pg.PgCancelCommand;
import com.programmers.kdt.payment.client.pg.PgCancelResult;
import com.programmers.kdt.payment.client.pg.PgClient;
import com.programmers.kdt.payment.client.pg.PgClientException;
import com.programmers.kdt.payment.client.pg.PgIdempotencyKeys;
import com.programmers.kdt.payment.client.pg.PgResponseMismatchException;
import com.programmers.kdt.payment.entity.Payment;
import com.programmers.kdt.payment.entity.PaymentStatus;
import com.programmers.kdt.payment.exception.PointErrorCode;
import com.programmers.kdt.payment.repository.PaymentRepository;
import com.programmers.kdt.payment.service.PointService;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskType;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskWriter;
import com.programmers.kdt.payment.service.tx.PaymentTxOps;
import com.programmers.kdt.payment.service.tx.PgOutcome;
import com.programmers.kdt.payment.service.util.PointEventIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_POINT_ROLLBACK_ATTEMPTS = 3;
    private static final Duration GIVE_UP_THRESHOLD = Duration.ofMinutes(10);
    private static final Duration MIN_PENDING_AGE = Duration.ofSeconds(40);

    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;
    private final PaymentTxOps paymentTxOps;
    private final PointService pointService;
    private final ReconciliationTaskWriter reconciliationTaskWriter;

    @Scheduled(fixedDelay = 60000)
    public void reconcilePayments() {
        LocalDateTime cutoffTime = LocalDateTime.now().minus(MIN_PENDING_AGE);
        Page<Payment> payments = paymentRepository.findByPaymentStatusAndModifiedAtBefore(
                PaymentStatus.CONFIRM_PENDING_VERIFICATION,
                cutoffTime,
                PageRequest.of(0, BATCH_SIZE, Sort.by("modifiedAt").ascending())
        );
        if (payments.isEmpty()) return;

        int resolved = 0;
        for (Payment payment : payments) {
            try {
                if (reconcilePayment(payment)) resolved++;
            } catch (Exception e) {
                log.error("PG 승인 재조회 처리 중 예상치 못한 예외 - paymentId={}", payment.getId(), e);
            }
        }
        log.info("PG 승인 재조회 대상 {}건 중 {}건 처리", payments.getNumberOfElements(), resolved);
    }

    private boolean reconcilePayment(Payment payment) {
        Long usedPoint = resolvedUsedPoint(PointEventIds.useEventId(payment.getOrderId(), payment.getAttemptSeq()));
        Long expectedAmount = payment.getAmount() - usedPoint;

        PgOutcome outcome;
        try {
            PgApproveResult result = pgClient.select(payment.getPaymentKey(), payment.getPgOrderId(), expectedAmount);
            outcome = result.success() ? PgOutcome.SUCCESS : PgOutcome.EXPLICIT_FAIL;
        } catch (PgClientException e) {
            outcome = PgOutcome.EXPLICIT_FAIL;
        } catch (PgResponseMismatchException e) {
            // PG는 "성공"이라 답했는데 내용이 안 맞음 - 그대로 확정지으면 위험하니 재조회 실패와 동일하게
            // AMBIGUOUS로 다뤄서 재시도/give-up 경로를 그대로 타게 하되, 발생 즉시 눈에 띄게 기록해둔다.
            log.error("[PG_RESPONSE_MISMATCH] 재조회 응답이 기대값과 다름 - paymentId={}, orderId={}", payment.getId(), payment.getOrderId(), e);
            reconciliationTaskWriter.record(ReconciliationTaskType.PG_RESPONSE_MISMATCH, payment.getId(),
                    "orderId=" + payment.getOrderId() + ", " + e.getMessage());
            outcome = PgOutcome.AMBIGUOUS;
        } catch (RestClientException e) {
            outcome = PgOutcome.AMBIGUOUS;
        }

        if (outcome == PgOutcome.AMBIGUOUS) return handleAmbiguous(payment);

        // PAYMENT_CONFIRMED/PAYMENT_FAILED 이벤트는 applyReconcileResult 안에서 이미 outbox에 기록됨
        Payment resolved = paymentTxOps.applyReconcileResult(payment.getId(), outcome);
        if (outcome != PgOutcome.SUCCESS) {
            rollbackFailedPoint(resolved);
        }

        return true;
    }

    private boolean handleAmbiguous(Payment payment) {
        Duration pending = Duration.between(payment.getModifiedAt(), LocalDateTime.now());
        if (pending.compareTo(GIVE_UP_THRESHOLD) < 0)  {
            return false;
        }

        log.error("[PG_CONFIRM_RECONCILIATION_NEEDED] 재조회 시간 초과로 결제 실패 처리 - paymentId={}, orderId={}, pendingSince={} ", payment.getId(), payment.getOrderId(), payment.getModifiedAt());
        reconciliationTaskWriter.record(ReconciliationTaskType.PG_CONFIRM_TIMEOUT_FORCE_FAILED, payment.getId(),
                "orderId=" + payment.getOrderId() + ", pendingSince=" + payment.getModifiedAt());

        Long usedPoint = resolvedUsedPoint(PointEventIds.useEventId(payment.getOrderId(), payment.getAttemptSeq()));
        attemptGiveUpCancel(payment, usedPoint);

        paymentTxOps.applyReconcileResult(payment.getId(), PgOutcome.EXPLICIT_FAIL);
        rollbackPointWithRetry(payment, usedPoint);
        return true;
    }

    // 재조회를 포기하고 강제 실패 처리하기 전에 안전망으로 PG 취소를 시도한다.
    // 실제로는 승인이 안 됐던 경우(select가 계속 응답 없었을 뿐) PG가 "결제 없음" 류로 거절하니 안전하고,
    // 실제로는 승인돼 있었던 경우 이 취소로 "돈 받고 환불 안 됨" 상태를 막는다.
    // 이 취소 시도마저 결과가 불확실하면(응답 실패/네트워크 오류) 돈이 PG쪽에 묶여 있을 수 있으므로 별도로 기록 -
    // 이후 일일 대사 배치(PaymentDailySettlementReconciliationScheduler)가 이 결제를 다시 훑어 재포착한다.
    private void attemptGiveUpCancel(Payment payment, Long usedPoint) {
        Long cancelAmount = payment.getAmount() - usedPoint;
        try {
            PgCancelResult result = pgClient.cancel(new PgCancelCommand(payment.getPaymentKey(), cancelAmount,
                    "CONFIRM_TIMEOUT_GIVE_UP", PgIdempotencyKeys.cancelKey(payment.getId())));
            if (!result.success()) {
                recordCancelUncertain(payment, "PG 취소 응답 success=false");
            }
        } catch (PgClientException e) {
            // 승인 자체가 없었을 가능성이 높은 거절(존재하지 않는 결제 등) - 대사 테이블에는 안 남기고 로그만
            log.warn("재조회 포기 시 PG 취소가 거절됨(승인 전이었을 가능성) - paymentId={}, pgCode={}", payment.getId(), e.getPgErrorCode());
        } catch (RestClientException e) {
            recordCancelUncertain(payment, "PG 취소 요청 자체가 실패(응답 없음)");
        }
    }

    private void recordCancelUncertain(Payment payment, String detail) {
        log.error("[PG_CONFIRM_TIMEOUT_CANCEL_UNCERTAIN] 재조회 포기 후 안전망 PG 취소도 불확실 - paymentId={}, orderId={}",
                payment.getId(), payment.getOrderId());
        reconciliationTaskWriter.record(ReconciliationTaskType.PG_CONFIRM_TIMEOUT_CANCEL_UNCERTAIN, payment.getId(),
                "orderId=" + payment.getOrderId() + ", detail=" + detail);
    }

    private void rollbackFailedPoint(Payment payment) {
        Long usedPoint = resolvedUsedPoint(PointEventIds.useEventId(payment.getOrderId(), payment.getAttemptSeq()));
        rollbackPointWithRetry(payment, usedPoint);
    }

    private Long resolvedUsedPoint(String eventId) {
        Long usedPoint = pointService.findUsedAmount(eventId);
        return usedPoint == null ? 0L : usedPoint;
    }

    private void rollbackPointWithRetry(Payment payment, Long usedPoint) {
        if (usedPoint <= 0) return;
        Long orderId = payment.getOrderId();
        Long paymentId = payment.getId();
        int attemptSeq = payment.getAttemptSeq();
        for (int attempt = 1; attempt <= MAX_POINT_ROLLBACK_ATTEMPTS; attempt++) {
            try {
                pointService.rollbackPoint(PointEventIds.useEventId(orderId, attemptSeq), usedPoint,
                        PointEventIds.rollbackFailEventId(orderId, attemptSeq), true);
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
}

