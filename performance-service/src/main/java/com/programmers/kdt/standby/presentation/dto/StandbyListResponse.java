package com.programmers.kdt.standby.presentation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StandbyListResponse(
        Long standbyId,
        Long performanceId,
        Long sessionNum,
        String performanceTitle,
        LocalDateTime performanceStartAt,
        List<String> zones,
        String matchedZone,
        Long ticketId,
        String status,
        LocalDateTime reservedAt,
        LocalDateTime expiredAt
) {
}
