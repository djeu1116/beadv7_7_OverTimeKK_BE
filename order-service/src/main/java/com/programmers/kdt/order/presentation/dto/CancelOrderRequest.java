package com.programmers.kdt.order.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record CancelOrderRequest(
        String reason
) {
}
