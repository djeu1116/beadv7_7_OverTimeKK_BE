package com.programmers.kdt.payment.scheduler;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.client.pg.PgApproveResult;
import com.programmers.kdt.payment.client.pg.PgClient;
import com.programmers.kdt.payment.client.pg.PgClientException;
import com.programmers.kdt.payment.entity.Payment;
import com.programmers.kdt.payment.entity.PaymentStatus;
import com.programmers.kdt.payment.exception.PointErrorCode;
import com.programmers.kdt.payment.repository.PaymentRepository;
import com.programmers.kdt.payment.service.PointService;
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
        PgOutcome outcome;
        try {
            PgApproveResult result = pgClient.select(payment.getPaymentKey());
            outcome = result.success() ? PgOutcome.SUCCESS : PgOutcome.EXPLICIT_FAIL;
        } catch (PgClientException e) {
            outcome = PgOutcome.EXPLICIT_FAIL;
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
        Payment resolved = paymentTxOps.applyReconcileResult(payment.getId(), PgOutcome.EXPLICIT_FAIL);
        rollbackFailedPoint(resolved);
        return true;
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
                    return;
                }
            }
        }
    }
}

