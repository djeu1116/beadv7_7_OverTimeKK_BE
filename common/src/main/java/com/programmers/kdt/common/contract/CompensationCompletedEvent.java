package com.programmers.kdt.common.contract;

public record CompensationCompletedEvent(
        Long orderId,
        Long paymentId
) {
}
