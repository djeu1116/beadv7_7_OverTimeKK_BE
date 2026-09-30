package com.programmers.kdt.performance.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.programmers.kdt.performance.domain.entity.Performance;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PerformanceDetailResponse(
        Long performanceId,
        String title,
        String description,
        Long runtime,
        LocalDate startDate,
        LocalDate endDate,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime ticketOpenAt,
        Long hallId,
        String postUrl
) {
    public static PerformanceDetailResponse from(Performance performance, String postUrl) {
        return new PerformanceDetailResponse(
                performance.getPerformanceId(),
                performance.getTitle(),
                performance.getDescription(),
                performance.getRuntime(),
                performance.getStartDate(),
                performance.getEndDate(),
                performance.getTicketOpenAt(),
                performance.getHallId(),
                postUrl)
                ;
    }
}
