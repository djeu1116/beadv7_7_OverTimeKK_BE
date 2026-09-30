package com.programmers.kdt.performance.application.service;

import com.programmers.kdt.performance.presentation.dto.EndedTicketsResponse;
import com.programmers.kdt.performance.presentation.dto.FindPerformancesResponse;
import com.programmers.kdt.performance.presentation.dto.PerformanceSessionSeatResponse;
import com.programmers.kdt.performance.presentation.dto.SellerPerformanceResponse;
import com.programmers.kdt.performance.domain.entity.PerformanceStatus;

import java.time.LocalDate;
import java.util.List;

public interface FindPerformanceService {
    FindPerformancesResponse findPerformances(PerformanceStatus status, int page);

    EndedTicketsResponse findEndedPerformanceTickets(LocalDate from, LocalDate to);

    String getPerformanceTitle(Long performanceId);

    PerformanceSessionSeatResponse findPerformanceSessionSeats(Long performanceId);

    List<SellerPerformanceResponse> findSellerPerformances(Long sellerId);

    FindPerformancesResponse searchPerformancesByTitle(String title);
}
