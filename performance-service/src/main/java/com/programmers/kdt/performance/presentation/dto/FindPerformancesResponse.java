package com.programmers.kdt.performance.presentation.dto;

import java.util.List;

public record FindPerformancesResponse(
        Long pageCount,
        List<FindPerformanceDto> performances
) {
}
