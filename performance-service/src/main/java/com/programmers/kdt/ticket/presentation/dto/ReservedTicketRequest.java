package com.programmers.kdt.ticket.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReservedTicketRequest(
        @NotNull(message = "티켓 정보를 입력해주세요.")
        Long ticketId,
        @NotBlank(message = "점유 Key를 입력해주세요.")
        String holdKey,
        @NotNull(message = "구매자ID를 입력해주세요.")
        Long userId
) {
}
