package com.programmers.kdt.order.domain.event;

public record TicketCancelRequestEvent(
        Long ticketId,
        Long userId,
        Long orderId
) {
}
