package com.programmers.kdt.payment.entity;

import com.programmers.kdt.common.entity.BaseTimeEntity;
import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_refund")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRefund extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "refund_amount", nullable = false)
    private Long refundAmount;

    @Column(name = "reason")
    private String reason;

    public static PaymentRefund create(Long paymentId, Long refundAmount, String reason) {
        if (paymentId == null) {
            throw new BusinessException(PaymentErrorCode.MISSING_PAYMENT_ID);
        }
        if (refundAmount == null || refundAmount <= 0) {
            throw new BusinessException(PaymentErrorCode.ZERO_REFUND_AMOUNT, refundAmount);
        }
        PaymentRefund refund = new PaymentRefund();
        refund.paymentId = paymentId;
        refund.refundAmount = refundAmount;
        refund.reason = reason;
        return refund;
    }
}
