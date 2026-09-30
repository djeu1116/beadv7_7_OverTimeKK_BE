package com.programmers.kdt.ticket.presentation.dto;

import java.time.LocalDateTime;

public record CheckTicketHoldAvailableResponse(
        Long ticketId,
        Long price,
        LocalDateTime holdExpiredAt,
        String holdKey
) {
}
