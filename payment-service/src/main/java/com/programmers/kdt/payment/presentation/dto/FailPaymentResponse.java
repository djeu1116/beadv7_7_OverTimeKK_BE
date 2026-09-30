package com.programmers.kdt.payment.presentation.dto;

import com.programmers.kdt.payment.domain.entity.Payment;

public record FailPaymentResponse(
        Long paymentId,
        String status
) {
    public static FailPaymentResponse from(Payment payment) {
        return new FailPaymentResponse(payment.getId(), payment.getPaymentStatus().name());
    }
}
