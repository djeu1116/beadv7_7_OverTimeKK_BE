package com.programmers.kdt.payment.presentation.dto;

import com.programmers.kdt.payment.infrastructure.client.pg.PgReadyResult;
import com.programmers.kdt.payment.domain.entity.Payment;

public record CreatePaymentResponse(
        Long paymentId,
        String status,
        String orderId,
        Long amount,
        String transactionKey,
        String redirectionUrl
) {

    public static CreatePaymentResponse of(Payment payment, PgReadyResult readyResult) {
        return new CreatePaymentResponse(
                payment.getId(), payment.getPaymentStatus().name(),
                readyResult.orderId(), payment.getAmount(),
                readyResult.transactionKey(), readyResult.redirectionUrl()
        );
    }
}
