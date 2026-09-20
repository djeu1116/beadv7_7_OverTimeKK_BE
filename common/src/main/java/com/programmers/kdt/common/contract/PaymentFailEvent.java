package com.programmers.kdt.common.contract;

public record PaymentFailEvent(
        Long orderId,
        Long paymentId,
        String reason
) {
}
