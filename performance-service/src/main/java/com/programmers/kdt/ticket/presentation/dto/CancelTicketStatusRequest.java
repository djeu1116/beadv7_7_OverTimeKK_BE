package com.programmers.kdt.ticket.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record CancelTicketStatusRequest(
        @NotNull(message =  "취소할 티켓을 입력하세요.")
        Long ticketId,

        @NotNull(message = "사용자 ID를 입력하세요.")
        Long userId
) {
}
