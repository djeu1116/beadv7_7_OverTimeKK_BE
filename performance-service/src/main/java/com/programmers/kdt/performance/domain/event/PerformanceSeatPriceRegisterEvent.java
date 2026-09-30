package com.programmers.kdt.performance.domain.event;

import com.programmers.kdt.performance.presentation.dto.PerformanceSeatPriceRequest;
import com.programmers.kdt.performance.domain.entity.Performance;

import java.util.List;

public record PerformanceSeatPriceRegisterEvent(Performance performance, List<PerformanceSeatPriceRequest> seatPriceRequests) {
}