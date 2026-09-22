package com.programmers.kdt.payment.dto;

import com.programmers.kdt.payment.client.pg.PgReadyResult;
import com.programmers.kdt.payment.entity.Payment;

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
