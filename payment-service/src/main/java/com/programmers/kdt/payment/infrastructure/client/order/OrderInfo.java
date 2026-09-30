package com.programmers.kdt.payment.infrastructure.client.order;

public record OrderInfo(
        Long orderId,
        Long userId,
        Long totalAmount
) {
}
