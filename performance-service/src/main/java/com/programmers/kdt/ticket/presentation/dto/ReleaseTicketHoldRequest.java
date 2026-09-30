package com.programmers.kdt.ticket.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record ReleaseTicketHoldRequest(
        @NotNull(message = "티켓 정보를 입력해주세요.")
        Long ticketId,

        String holdKey
) {
}
