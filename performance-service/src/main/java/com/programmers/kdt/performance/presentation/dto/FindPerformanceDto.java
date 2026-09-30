package com.programmers.kdt.performance.presentation.dto;

import java.time.LocalDate;

public record FindPerformanceDto(
        Long performanceId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String hallName,
        String postUrl
) {

}
