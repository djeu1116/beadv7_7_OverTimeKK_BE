package com.programmers.kdt.ticket.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CheckTicketHoldAvailableRequest(
        @NotNull(message  = "티켓 정보를 입력해주세요.")
        Long ticketId,

        @NotBlank(message = "일반/대기주문 케이스를 입력해주세요.")
        String orderType
) {
}
