package com.programmers.kdt.common.contract;

public record PaymentConfirmEvent(
        Long orderId,
        Long paymentId
) {
}
