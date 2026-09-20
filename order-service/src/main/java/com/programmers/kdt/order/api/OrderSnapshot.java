package com.programmers.kdt.order.api;

public record OrderSnapshot(
        Long orderId,
        Long userId,
        Long totalAmount
) {
}
