package com.programmers.kdt.standby.presentation.controller;

import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.standby.presentation.dto.CreateStandbyRequest;
import com.programmers.kdt.standby.presentation.dto.CreateStandbyResponse;
import com.programmers.kdt.standby.presentation.dto.StandbyListResponse;
import com.programmers.kdt.standby.presentation.dto.StandbyRankResponse;
import com.programmers.kdt.standby.application.service.StandbyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/standby")
@RequiredArgsConstructor
public class StandbyController {

    private final StandbyService standbyService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateStandbyResponse>> issueStandby(
            @Valid @RequestBody CreateStandbyRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        CreateStandbyResponse response = standbyService.applyStandby(
                userId, request.performanceId(), request.sessionNum(), request.zones());
        return ResponseEntity.created(URI.create("/api/standby/" + response.standbyId()))
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<List<StandbyListResponse>> getStandby(@RequestHeader("X-User-Id") Long userId) {
        List<StandbyListResponse> response = standbyService.getStandby(userId);
        return ApiResponse.success(response);
    }

    @GetMapping("/{standbyId}")
    public  ApiResponse<StandbyRankResponse> getStandbyRank(
            @PathVariable Long standbyId,
            @RequestHeader("X-User-Id") Long userId) {
        StandbyRankResponse response = standbyService.getStandbyRank(standbyId,userId);
        return ApiResponse.success(response);
    }

    @DeleteMapping("/{standbyId}")
    public ResponseEntity<Void> cancelStandby(
            @PathVariable Long standbyId,
            @RequestHeader("X-User-Id") Long userId) {
        standbyService.cancelStandby(standbyId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{standbyId}/zones/{zone}")
    public ResponseEntity<Void> cancelStandbyZone(
            @PathVariable Long standbyId,
            @PathVariable String zone,
            @RequestHeader("X-User-Id") Long userId) {
        standbyService.cancelZone(standbyId, userId, zone);
        return ResponseEntity.noContent().build();
    }
}
