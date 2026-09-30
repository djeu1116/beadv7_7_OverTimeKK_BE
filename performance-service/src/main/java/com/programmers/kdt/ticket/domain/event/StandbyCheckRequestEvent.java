package com.programmers.kdt.ticket.domain.event;

public record StandbyCheckRequestEvent(
        Long performanceId,
        Long sessionNum,
        String zone,
        Long ticketId
) {
}
