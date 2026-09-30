package com.programmers.kdt.ticket.presentation.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OrderTicketRequest(
        @NotEmpty(message = "티켓 ID 목록 입력은 필수입니다.")
        List<Long> ticketIds
) {
}
