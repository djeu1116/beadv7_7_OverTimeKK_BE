package com.programmers.kdt.payment.domain.entity;

import com.programmers.kdt.common.entity.BaseTimeEntity;
import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.infrastructure.converter.PaymentKeyConverter;
import com.programmers.kdt.payment.domain.exception.PaymentErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "refunded_amount", nullable = false)
    private Long refundedAmount;

    @Convert(converter = PaymentKeyConverter.class)
    @Column(name = "payment_key")
    private String paymentKey; // PG 참조값

    @Column(name = "pg_order_id")
    private String pgOrderId; // PG사 주문번호

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "attempt_seq", nullable = false)
    private Integer attemptSeq;  // 결제 시도 순번(0부터), 재시도마다 증가

    //결제 생성 메서드
    public static Payment create(Long orderId, Long userId, Long amount) {
        if (orderId == null) { // 어떤 예외처리 ?
            throw new BusinessException(PaymentErrorCode.MISSING_ORDER_ID);
        }

        if (amount == null || amount <= 0) {
            throw new BusinessException(PaymentErrorCode.ZERO_PAYMENT_AMOUNT, amount);
        }
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.userId = userId;
        payment.amount = amount;
        payment.paymentStatus = PaymentStatus.READY; //상태는 READY로 생성
        payment.refundedAmount = 0L; //환불금액은 0
        payment.attemptSeq = 0;
        return payment;
    }

    //결제 승인 메서드 READY -> PAID
    public void approve() {
        if (paymentStatus == PaymentStatus.PAID) {
            return; // 이미 결제된 경우 중복 이벤트 무시
        }
        if (!isReady()) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS, this.paymentStatus);
        }
        this.paymentStatus = PaymentStatus.PAID;
    }

    public void markPending() {
        if (paymentStatus == PaymentStatus.PAID) {
            return;
        }
        if (!isReady()) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS, this.paymentStatus);
        }
        this.paymentStatus = PaymentStatus.CONFIRM_PENDING_VERIFICATION;
    }

    public void confirmVerifiedSuccess() {
        if (paymentStatus == PaymentStatus.PAID) return;
        if (paymentStatus != PaymentStatus.CONFIRM_PENDING_VERIFICATION) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS, this.paymentStatus);
        }
        this.paymentStatus = PaymentStatus.PAID;
    }

    public void confirmVerifiedFail() {
        if (paymentStatus == PaymentStatus.FAILED) return;
        if (paymentStatus != PaymentStatus.CONFIRM_PENDING_VERIFICATION) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS, this.paymentStatus);
        }
        this.paymentStatus = PaymentStatus.FAILED;
    }

    // PG사 키 등록
    public void assignPaymentKey(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new BusinessException(PaymentErrorCode.MISSING_TRANSACTION_KEY, paymentKey);
        }
        this.paymentKey = paymentKey;
    }

    public void retryReady(String pgOrderId, int attemptSeq) {
        if (paymentStatus != PaymentStatus.FAILED) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS, this.paymentStatus);
        }
        this.paymentStatus = PaymentStatus.READY;
        this.pgOrderId = pgOrderId;
        this.paymentKey = null;
        this.attemptSeq = attemptSeq;
    }

    public void assignPgOrderId(String pgOrderId) {
        this.pgOrderId = pgOrderId;
    }

    // 결제 실패 메서드 READY -> FAILED
    public void fail() {
        if (paymentStatus == PaymentStatus.FAILED) {
            return; // 이미 취소된 경우 중복 이벤트 무시
        }
        if (!isReady()) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS, this.paymentStatus);
        }
        this.paymentStatus = PaymentStatus.FAILED;
    }

    // 환불 요청 접수 (동기 단계 호출)
    public void requestRefund() {
        if (paymentStatus == PaymentStatus.REFUND_PENDING) {
            throw new BusinessException(PaymentErrorCode.REFUND_ALREADY_IN_PROGRESS);
        }
        if (paymentStatus != PaymentStatus.PAID) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS, this.paymentStatus);
        }
        this.paymentStatus = PaymentStatus.REFUND_PENDING;
    }

    // 환불 처리 완료 (비동기 컨슈머 호출)
    public void completeRefund(Long refundAmount) {
        if (paymentStatus != PaymentStatus.REFUND_PENDING) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS, this.paymentStatus);
        }

        if (refundAmount == null || refundAmount < 0 || refundAmount > amount) {
            throw new BusinessException(PaymentErrorCode.INVALID_REFUND_AMOUNT, amount, refundAmount);
        }

        this.refundedAmount = refundAmount;
        this.paymentStatus = PaymentStatus.CANCELLED;
    }

    // 환불 처리 실패(PG 실패, 공연 정보 조회 실패등 - 롤백)
    public void failRefund() {
        if (paymentStatus != PaymentStatus.REFUND_PENDING) {
            return;
        }
        this.paymentStatus = PaymentStatus.PAID; // 실패 시 원상복귀
    }

    // READY 상태인지 판별
    private boolean isReady() {
        return paymentStatus == PaymentStatus.READY;
    }

}
