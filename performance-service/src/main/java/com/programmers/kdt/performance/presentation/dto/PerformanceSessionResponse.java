package com.programmers.kdt.performance.presentation.dto;

import com.programmers.kdt.performance.domain.entity.PerformanceSession;

import java.time.LocalDateTime;

public record PerformanceSessionResponse(Long sessionNum, Long performanceId, String actor, LocalDateTime performanceStartAt) {
    public static PerformanceSessionResponse from (PerformanceSession session) {
        return new PerformanceSessionResponse(
                session.getPerformanceSessionId().getSessionNum(),
                session.getPerformanceSessionId().getPerformanceId(),
                session.getActor(),
                session.getPerformanceStartAt());
    }
}
