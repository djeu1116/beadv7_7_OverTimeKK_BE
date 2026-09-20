package com.programmers.kdt.common.contract;

public record RefundFailedEvent(
        Long orderId,
        Long paymentId,
        String reason
) {
}
