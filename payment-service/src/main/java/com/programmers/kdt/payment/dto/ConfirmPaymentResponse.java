package com.programmers.kdt.payment.dto;

import com.programmers.kdt.payment.entity.Payment;

public record ConfirmPaymentResponse(
        Long paymentId,
        String status
) {

    public static ConfirmPaymentResponse from(Payment payment) {
        return new ConfirmPaymentResponse(payment.getId(), payment.getPaymentStatus().name());
    }
}
