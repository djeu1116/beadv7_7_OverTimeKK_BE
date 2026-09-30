package com.programmers.kdt.performance.presentation.controller;

import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.performance.presentation.dto.RegisterPerformanceRequest;
import com.programmers.kdt.performance.application.service.PerformanceV2Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/performances")
@RequiredArgsConstructor
public class PerformanceV2Controller {

    private final PerformanceV2Service performanceService;

    @PostMapping
    public ApiResponse<?> registerV2Performance(
            @Valid @RequestBody RegisterPerformanceRequest request,
            @RequestHeader("X-User-Id") Long sellerId) {
        performanceService.registerPerformanceInformation(request, sellerId);
        return ApiResponse.success(null);
    }
}
