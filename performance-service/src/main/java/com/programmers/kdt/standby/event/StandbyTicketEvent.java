package com.programmers.kdt.standby.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record StandbyTicketEvent(
        Long standbyId,
        Long ticketId,
        Long standbyUserId,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime standbyExpiredAt
) {
}
