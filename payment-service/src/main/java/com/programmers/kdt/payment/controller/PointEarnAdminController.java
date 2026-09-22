package com.programmers.kdt.payment.controller;

import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.payment.scheduler.PointEarnScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

// 현재는 관리자 권한 체크 없이 열려있음. -> 추후에 관리자 인증 추가해야함.
@RestController
@RequestMapping("/api/admin/point-earn")
@RequiredArgsConstructor
public class PointEarnAdminController {

    private final PointEarnScheduler pointEarnScheduler;

    // 배치 실패/누락으로 특정 날짜 수동으로 재처리 (포인트 지급)
    @PostMapping("/replay")
    public ApiResponse<Void> replay(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        pointEarnScheduler.earnPointsForEndedPerformances(from, to);
        return ApiResponse.success(null);
    }

}
