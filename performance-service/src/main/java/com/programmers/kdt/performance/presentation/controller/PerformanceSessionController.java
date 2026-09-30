package com.programmers.kdt.performance.presentation.controller;

import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.performance.presentation.dto.PerformanceSessionRequest;
import com.programmers.kdt.performance.presentation.dto.PerformanceSessionResponse;
import com.programmers.kdt.performance.application.service.PerformanceSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/performance")
public class PerformanceSessionController {

    private final PerformanceSessionService performanceSessionService;

    @PostMapping("/session")
    public ApiResponse<?> registerPerformanceSession(
            @Valid @RequestBody PerformanceSessionRequest request,
            @RequestHeader("X-User-Id") Long sellerId) {
        PerformanceSessionResponse response = performanceSessionService.registerPerformanceSession(request, sellerId);
        return ApiResponse.success(response);
    }

    @PutMapping("/session")
    public ApiResponse<?> changePerformanceSession(
            @Valid @RequestBody PerformanceSessionRequest request,
            @RequestHeader("X-User-Id") Long sellerId) {
        PerformanceSessionResponse response = performanceSessionService.changePerformanceSession(request, sellerId);
        return ApiResponse.success(response);
    }

    @GetMapping("/{performanceId}/session")
    public ApiResponse<?> getPerformanceSessions(@Valid @PathVariable Long performanceId) {
        List<PerformanceSessionResponse> responses = performanceSessionService.findAllPerformanceSessionsByPerformanceId(performanceId);
        return ApiResponse.success(responses);
    }

    @GetMapping("/{performanceId}/session/{sessionNum}")
    public ApiResponse<?> getPerformanceSession(@Valid @PathVariable Long performanceId, @PathVariable Long sessionNum) {
        PerformanceSessionResponse response = performanceSessionService.getPerformanceSession(sessionNum, performanceId);
        return ApiResponse.success(response);
    }

    @DeleteMapping("/{performanceId}/session/{sessionNum}")
    public ApiResponse<?> deletePerformanceSession(
            @Valid @PathVariable Long performanceId, @PathVariable Long sessionNum,
            @RequestHeader("X-User-Id") Long sellerId) {
        performanceSessionService.deletePerformanceSession(sessionNum, performanceId, sellerId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{performanceId}/session")
    public ApiResponse<?> deletePerformanceSessions(
            @PathVariable Long performanceId,
            @RequestHeader("X-User-Id") Long sellerId) {
        performanceSessionService.deletePerformanceSessions(performanceId, sellerId);
        return ApiResponse.success(null);
    }
}
