package com.programmers.kdt.performance.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.programmers.kdt.performance.domain.entity.Performance;
import com.programmers.kdt.performance.domain.entity.PerformanceSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record RegisterPerformanceSessionRequest(
        @NotNull(message = "회차정보 입력은 필수입니다.")
        Long sessionNum,
        @NotBlank(message = "배우 입력은 필수입니다.")
        String actor,

        @NotNull(message = "해당 회차 공연 시작시간 입력은 필수입니다.")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime performanceStartAt
) {
    public PerformanceSession toSession(Performance performance) {
        return PerformanceSession.createInitial(sessionNum, performance, actor, performanceStartAt);
    }
}
