package com.programmers.kdt.ticket.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ValidateTicketHoldRequest(
        @NotNull(message = "티켓 정보를 입력해주세요.")
        Long ticketId,

        @NotNull(message = "주문자 정보를 입력해주세요.")
        Long userId,

        @NotNull(message = "가격 정보를 입력해주세요.")
        Long price,

        @NotBlank(message = "점유 키 정보를 입력해주세요.")
        String holdKey
) {
}