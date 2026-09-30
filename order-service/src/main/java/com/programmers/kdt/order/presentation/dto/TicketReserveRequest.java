package com.programmers.kdt.order.presentation.dto;

public record TicketReserveRequest(
        Long ticketId,
        String holdKey,
        Long userId
) {
}
