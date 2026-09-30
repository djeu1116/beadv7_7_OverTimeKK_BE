package com.programmers.kdt.payment.presentation.dto;

import com.programmers.kdt.payment.domain.entity.Payment;

import java.time.LocalDateTime;

public record GetPaymentHistoryResponse(
        Long paymentId,
        Long orderId,
        Long amount,
        String status,
        LocalDateTime createdAt
) {

    public static GetPaymentHistoryResponse from(Payment payment) {
        return new GetPaymentHistoryResponse(
                payment.getId(), payment.getOrderId(), payment.getAmount(),
                payment.getPaymentStatus().name(), payment.getCreatedAt()
        );
    }
}
