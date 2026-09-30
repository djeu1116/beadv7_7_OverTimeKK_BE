package com.programmers.kdt.performance.domain.event;

import com.programmers.kdt.performance.presentation.dto.RegisterPerformanceSessionRequest;
import com.programmers.kdt.performance.domain.entity.Performance;

import java.util.List;

public record PerformanceSessionRegisterEvent(Performance performance, List<RegisterPerformanceSessionRequest> sessionRequests) {
}