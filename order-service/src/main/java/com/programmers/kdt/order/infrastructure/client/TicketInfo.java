package com.programmers.kdt.order.infrastructure.client;

public record TicketInfo(
        Long ticketId,
        String performanceName,
        String zone
) {
}
