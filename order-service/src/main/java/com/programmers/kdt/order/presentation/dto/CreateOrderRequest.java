package com.programmers.kdt.order.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateOrderRequest(
        @NotNull
        Long ticketId,

        @NotNull
        Long price,

        @NotNull
        LocalDateTime expiredAt,

        @NotBlank
        String holdKey

) {
}
