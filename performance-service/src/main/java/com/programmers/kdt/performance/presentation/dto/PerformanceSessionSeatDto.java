package com.programmers.kdt.performance.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record PerformanceSessionSeatDto(
        Long sessionNum,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime performanceStartAt,
        String actor,
        String zone,
        Long price,
        Long availableSeatCount
) {
}