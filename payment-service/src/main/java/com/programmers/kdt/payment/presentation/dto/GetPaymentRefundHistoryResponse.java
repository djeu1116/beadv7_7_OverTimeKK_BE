package com.programmers.kdt.payment.presentation.dto;

import com.programmers.kdt.payment.domain.entity.PaymentRefund;

import java.time.LocalDateTime;

public record GetPaymentRefundHistoryResponse(
        Long refundId,
        Long paymentId,
        Long refundAmount,
        String reason,
        LocalDateTime refundedAt
) {
    public static GetPaymentRefundHistoryResponse from(PaymentRefund refund) {
        return new GetPaymentRefundHistoryResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getRefundAmount(),
                refund.getReason(),
                refund.getCreatedAt()
        );
    }

}
