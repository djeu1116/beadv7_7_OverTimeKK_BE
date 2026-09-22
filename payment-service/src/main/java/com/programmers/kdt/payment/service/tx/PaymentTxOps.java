package com.programmers.kdt.payment.service.tx;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.contract.PaymentConfirmEvent;
import com.programmers.kdt.common.contract.PaymentFailEvent;
import com.programmers.kdt.payment.entity.Payment;
import com.programmers.kdt.payment.entity.PaymentStatus;
import com.programmers.kdt.payment.entity.outbox.OutboxEventType;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import com.programmers.kdt.payment.repository.PaymentRepository;
import com.programmers.kdt.payment.service.OutboxEventWriter;
import com.programmers.kdt.payment.service.PointService;
import com.programmers.kdt.payment.service.util.PointEventIds;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PaymentTxOps {

    private final PaymentRepository paymentRepository;
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

        enqueueResultEvent(payment, statusBefore, pgOutcome);
        return payment;
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


