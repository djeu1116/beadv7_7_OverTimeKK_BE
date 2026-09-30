package com.programmers.kdt.order.presentation.dto;

public record TicketCancelRequest(
        Long ticketId,
        Long userId
) {
}
