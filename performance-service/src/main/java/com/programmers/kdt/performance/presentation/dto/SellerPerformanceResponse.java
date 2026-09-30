package com.programmers.kdt.performance.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SellerPerformanceResponse(
        Long performanceId,
        String title,
        String hallName,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime ticketOpenAt,
        LocalDate endDate,
        Long totSession,
        Long zoneCount
) {
}