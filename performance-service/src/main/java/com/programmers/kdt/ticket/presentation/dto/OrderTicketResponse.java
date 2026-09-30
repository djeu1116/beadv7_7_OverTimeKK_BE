package com.programmers.kdt.ticket.presentation.dto;

public record OrderTicketResponse(
        Long ticketId,
        String performanceName,
        String zone
) {
}
