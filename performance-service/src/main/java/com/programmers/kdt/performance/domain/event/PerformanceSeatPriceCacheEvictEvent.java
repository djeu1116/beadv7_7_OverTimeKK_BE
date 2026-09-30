package com.programmers.kdt.performance.domain.event;

import java.util.List;

public record PerformanceSeatPriceCacheEvictEvent(Long performanceId, List<String> zones) {
}