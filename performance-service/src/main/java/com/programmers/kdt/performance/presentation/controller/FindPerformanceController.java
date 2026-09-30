package com.programmers.kdt.performance.presentation.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.programmers.kdt.common.exception.CommonErrorCode;
import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.performance.presentation.dto.EndedTicketsResponse;
import com.programmers.kdt.performance.presentation.dto.FindPerformancesResponse;
import com.programmers.kdt.performance.presentation.dto.PerformanceSessionSeatResponse;
import com.programmers.kdt.performance.presentation.dto.SellerPerformanceResponse;
import com.programmers.kdt.performance.domain.entity.PerformanceStatus;
import com.programmers.kdt.performance.application.service.FindPerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/performances")
@RequiredArgsConstructor
public class FindPerformanceController {

    private final FindPerformanceService findPerformanceService;

    @GetMapping
    public ApiResponse<FindPerformancesResponse> findPerformances(
            @RequestParam(name = "status", required = false) PerformanceStatus status,
            @RequestParam(name = "page", defaultValue = "1") int page) {
        FindPerformancesResponse performances = findPerformanceService.findPerformances(status, page);
        return ApiResponse.success(performances);
    }

    @GetMapping("/ended")
    public ApiResponse<EndedTicketsResponse> findEndedPerformanceTickets(
            @JsonFormat(pattern = "yyyy-MM-dd") @RequestParam("from") LocalDate from,
            @JsonFormat(pattern = "yyyy-MM-dd") @RequestParam("to") LocalDate to) {
        if (from.isAfter(to)) {
            ApiResponse.fail(CommonErrorCode.INVALID_DATE_RANGE.getCode(), CommonErrorCode.INVALID_DATE_RANGE.getMessage());
        }
        EndedTicketsResponse performanceTickets = findPerformanceService.findEndedPerformanceTickets(from, to);
        return ApiResponse.success(performanceTickets);
    }

    @GetMapping("/{performanceId}/title")
    public ApiResponse<String> findPerformanceTitle(@PathVariable Long performanceId) {
        String title = findPerformanceService.getPerformanceTitle(performanceId);
        return ApiResponse.success(title);
    }

    @GetMapping("/{performanceId}/sessions/seats")
    public ApiResponse<PerformanceSessionSeatResponse> findPerformanceSessionSeats(@PathVariable Long performanceId) {
        PerformanceSessionSeatResponse response = findPerformanceService.findPerformanceSessionSeats(performanceId);
        return ApiResponse.success(response);
    }

    @GetMapping("/seller")
    public ApiResponse<List<SellerPerformanceResponse>> findSellerPerformances(@RequestHeader("X-User-Id") Long sellerId) {
        List<SellerPerformanceResponse> response = findPerformanceService.findSellerPerformances(sellerId);
        return ApiResponse.success(response);
    }

    @GetMapping("/search")
    public ApiResponse<FindPerformancesResponse> searchPerformancesByTitle(@RequestParam("title") String title) {
        FindPerformancesResponse response = findPerformanceService.searchPerformancesByTitle(title);
        return ApiResponse.success(response);
    }
}
