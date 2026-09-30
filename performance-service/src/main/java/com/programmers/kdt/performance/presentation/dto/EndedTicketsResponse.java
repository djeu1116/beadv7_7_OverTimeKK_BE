package com.programmers.kdt.performance.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

public record EndedTicketsResponse(
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate from,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate to,
        List<EndedTicketResponse> endedTickets
) {
}
