package com.programmers.kdt.payment.presentation.dto;

import com.programmers.kdt.payment.domain.entity.Payment;

public record ConfirmPaymentResponse(
        Long paymentId,
        String status
) {

    public static ConfirmPaymentResponse from(Payment payment) {
        return new ConfirmPaymentResponse(payment.getId(), payment.getPaymentStatus().name());
    }
}
