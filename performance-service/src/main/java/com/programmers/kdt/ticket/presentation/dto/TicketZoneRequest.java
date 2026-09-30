package com.programmers.kdt.ticket.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TicketZoneRequest(
        @NotNull(message = "공연정보를 입력하세요.")
        Long performanceId,
        @NotNull(message = "회차정보를 입력하세요")
        Long sessionNum,
        @NotBlank(message = "구역 정보를 입력하세요.")
        String zone
) {
}
