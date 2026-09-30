package com.programmers.kdt.payment.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreatePaymentRequest(
        @NotNull Long orderId,
        @NotNull Long amount,
        @PositiveOrZero Long usedPoint
) {

    public Long usedPointOrZero() {
        return usedPoint == null ? 0L : usedPoint;
    }
}
