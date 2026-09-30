package com.programmers.kdt.payment.infrastructure.client.pg.toss;

public record TossConfirmResponse(
        String paymentKey,
        String orderId,
        String status,
        String approvedAt,
        Long totalAmount
) {
}
