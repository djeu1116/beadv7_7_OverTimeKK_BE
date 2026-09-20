package com.programmers.kdt.common.contract;

public record OrderCancelRequestedEvent(
        Long orderId,
        String reason
) {
}
