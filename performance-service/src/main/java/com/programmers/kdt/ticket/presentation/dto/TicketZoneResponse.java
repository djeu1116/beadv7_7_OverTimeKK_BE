package com.programmers.kdt.ticket.presentation.dto;

// ticketStatus: 0 = 선택 가능(AVAILABLE), 1 = 선택 불가(그 외 상태)
public record TicketZoneResponse(
        Long ticketId,
        String seatRow,
        String seatNum,
        int ticketStatus
) {
}
