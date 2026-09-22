package com.programmers.kdt.payment.dto;

import com.programmers.kdt.payment.entity.Payment;

public record FailPaymentResponse(
        Long paymentId,
        String status
) {
    public static FailPaymentResponse from(Payment payment) {
        return new FailPaymentResponse(payment.getId(), payment.getPaymentStatus().name());
    }
}
