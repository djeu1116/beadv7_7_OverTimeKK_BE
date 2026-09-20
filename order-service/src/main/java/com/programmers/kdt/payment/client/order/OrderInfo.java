package com.programmers.kdt.payment.client.order;

public record OrderInfo(
        Long orderId,
        Long userId,
        Long totalAmount
) {
}
