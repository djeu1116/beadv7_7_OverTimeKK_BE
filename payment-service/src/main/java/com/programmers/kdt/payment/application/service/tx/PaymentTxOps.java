package com.programmers.kdt.payment.application.service.tx;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.contract.PaymentConfirmEvent;
import com.programmers.kdt.common.contract.PaymentFailEvent;
import com.programmers.kdt.payment.domain.entity.Payment;
import com.programmers.kdt.payment.domain.entity.PaymentAttempt;
import com.programmers.kdt.payment.domain.entity.PaymentStatus;
import com.programmers.kdt.payment.domain.entity.outbox.OutboxEventType;
import com.programmers.kdt.payment.domain.exception.PaymentErrorCode;
import com.programmers.kdt.payment.infrastructure.repository.PaymentAttemptRepository;
import com.programmers.kdt.payment.infrastructure.repository.PaymentRepository;
import com.programmers.kdt.payment.application.service.OutboxEventWriter;
import com.programmers.kdt.payment.application.service.PointService;
import com.programmers.kdt.payment.application.service.util.PointEventIds;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentTxOps {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PointService pointService;
    private final OutboxEventWriter outboxEventWriter;

    public record ReadyPaymentContext(Payment payment, Long usedPoint) {
    }

    // tx1 + 포인트 조회를 한 트랜잭션으로 묶음 — PG 호출 전 MySQL 왕복을 2회에서 1회로 줄임
    // 소유권 검증은 반드시 상태 변경(markPending)·커밋보다 먼저 한다 — 이 tx는 REQUIRES_NEW라
    // 여기서 커밋되면 호출부에서 뒤늦게 던지는 예외로도 되돌릴 수 없음
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReadyPaymentContext assignKeyAndCommit(Long paymentId, String transactionKey, Long userId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
        }

        if (payment.getPaymentStatus() != PaymentStatus.READY) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS, payment.getPaymentStatus());
        }

        payment.assignPaymentKey(transactionKey);
        payment.markPending();
        try {
            paymentRepository.saveAndFlush(payment);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONCURRENT_MODIFICATION);
        }
        findCurrentAttempt(payment).ifPresent(attempt -> {
            attempt.assignPaymentKey(transactionKey);
            attempt.markPending();
            paymentAttemptRepository.save(attempt);
        });

        Long usedPoint = pointService.findUsedAmount(PointEventIds.useEventId(payment.getOrderId(), payment.getAttemptSeq()));
        return new ReadyPaymentContext(payment, usedPoint == null ? 0L : usedPoint);
    }

    // 결과 반영 + outbox 기록을 같은 트랜잭션에 묶음 - 커밋 이후 이벤트 발행이 아니라
    // outbox row로 남겨서, 커밋 후 relay가 못 넘어가더라도(크래시 등) 나중에 재시도되게 함
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment applyConfirmResult(Long paymentId, PgOutcome pgOutcome) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        PaymentStatus statusBefore = payment.getPaymentStatus();
        switch (pgOutcome) {
            case SUCCESS -> payment.confirmVerifiedSuccess();
            case EXPLICIT_FAIL -> payment.confirmVerifiedFail();
            case AMBIGUOUS -> {}
        }

        try {
            paymentRepository.saveAndFlush(payment);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONCURRENT_MODIFICATION);
        }

        markAttemptOutcome(payment, pgOutcome);
        enqueueResultEvent(payment, statusBefore, pgOutcome);
        return payment;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment applyReconcileResult(Long paymentId, PgOutcome pgOutcome) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        PaymentStatus statusBefore = payment.getPaymentStatus();
        switch (pgOutcome) {
            case SUCCESS -> payment.confirmVerifiedSuccess();
            case EXPLICIT_FAIL -> payment.confirmVerifiedFail();
            case AMBIGUOUS -> {return payment;}
        }

        try {
            paymentRepository.saveAndFlush(payment);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONCURRENT_MODIFICATION);
        }

        markAttemptOutcome(payment, pgOutcome);
        enqueueResultEvent(payment, statusBefore, pgOutcome);
        return payment;
    }

    private Optional<PaymentAttempt> findCurrentAttempt(Payment payment) {
        return paymentAttemptRepository.findByPaymentIdAndAttemptSeq(payment.getId(), payment.getAttemptSeq());
    }

    // AMBIGUOUS는 호출부에서 걸러지고(early return) 여기 안 들어오므로 SUCCESS/EXPLICIT_FAIL만 온다
    private void markAttemptOutcome(Payment payment, PgOutcome pgOutcome) {
        findCurrentAttempt(payment).ifPresent(attempt -> {
            switch (pgOutcome) {
                case SUCCESS -> attempt.markPaid();
                case EXPLICIT_FAIL -> attempt.markFailed();
                case AMBIGUOUS -> { }
            }
            paymentAttemptRepository.save(attempt);
        });
    }

    // 상태가 실제로 바뀐 경우에만 기록 - confirmVerifiedSuccess/Fail은 이미 같은 상태면 조용히 무시하므로,
    // 그 경우까지 outbox에 넣으면 같은 이벤트가 중복 발행됨
    private void enqueueResultEvent(Payment payment, PaymentStatus statusBefore, PgOutcome pgOutcome) {
        if (payment.getPaymentStatus() == statusBefore) {
            return;
        }
        switch (pgOutcome) {
            case SUCCESS -> outboxEventWriter.enqueue(OutboxEventType.PAYMENT_CONFIRMED, payment.getId(),
                    new PaymentConfirmEvent(payment.getOrderId(), payment.getId()));
            case EXPLICIT_FAIL -> outboxEventWriter.enqueue(OutboxEventType.PAYMENT_FAILED, payment.getId(),
                    new PaymentFailEvent(payment.getOrderId(), payment.getId(), PaymentErrorCode.PG_REQUEST_FAILED.getMessage()));
            case AMBIGUOUS -> { }
        }
    }
}


