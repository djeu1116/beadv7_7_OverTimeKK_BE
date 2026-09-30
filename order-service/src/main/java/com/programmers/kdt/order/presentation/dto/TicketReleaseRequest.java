package com.programmers.kdt.order.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TicketReleaseRequest(

        @NotNull
        Long ticketId,

        @NotBlank
        String holdKey

) {
}