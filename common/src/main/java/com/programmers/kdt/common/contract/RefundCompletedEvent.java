package com.programmers.kdt.common.contract;

public record RefundCompletedEvent(
        Long orderId,
        Long paymentId
) {
}
