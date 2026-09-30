package com.programmers.kdt.order.domain.event;

public record TicketReleaseRequestEvent(
        Long orderId,
        Long ticketId,
        String holdKey
) {
}
