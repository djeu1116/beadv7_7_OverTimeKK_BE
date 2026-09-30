package com.programmers.kdt.standby.domain.event;

public record StandbyCheckResponseEvent(
        Long ticketId,
        boolean existsStandby
) {
}
