package com.programmers.kdt.order.application.api;

public record OrderSnapshot(
        Long orderId,
        Long userId,
        Long totalAmount
) {
}
