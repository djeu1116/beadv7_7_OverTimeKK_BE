package com.programmers.kdt.payment.client.pg.toss;

public record TossConfirmResponse(
        String paymentKey,
        String orderId,
        String status,
        String approvedAt
) {
}
