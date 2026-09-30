package com.programmers.kdt.performance.domain.entity;

import com.programmers.kdt.common.entity.BaseTimeEntity;
import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.exception.CommonErrorCode;
import com.programmers.kdt.performance.domain.exception.PerformanceErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class PerformanceSession extends BaseTimeEntity {

    @EmbeddedId
    private PerformanceSessionId performanceSessionId;

    // 복합키의 performanceId 부분을 이 FK가 채움(@MapsId)
    @MapsId("performanceId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id")
    private Performance performance;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private LocalDateTime performanceStartAt;

    public static PerformanceSession create(PerformanceSessionId performanceSessionId, Performance performance, String actor, LocalDateTime performanceStartAt) {
        PerformanceSession session = new PerformanceSession();
        session.performanceSessionId = performanceSessionId;
        session.performance = performance;
        session.actor = actor;
        session.performanceStartAt = performanceStartAt;
        return session;
    }

    public static PerformanceSession createInitial(Long sessionNum, Performance performance, String actor, LocalDateTime performanceStartAt) {
        PerformanceSession session = new PerformanceSession();
        session.performanceSessionId = new PerformanceSessionId(sessionNum, performance.getPerformanceId());
        session.performance = performance;
        session.actor = actor;
        session.performanceStartAt = performanceStartAt;
        return session;
    }

    public void changePerformanceSession(String actor, LocalDateTime performanceStartAt) {
        validPerformanceSessionInput(actor, performanceStartAt);
        validPossibleToChangePerformanceSessionTime(performanceStartAt);

        this.actor = actor;
        this.performanceStartAt = performanceStartAt;
    }
    public void validPossibleToChangePerformanceSessionTime(LocalDateTime performanceStartAt) {
        LocalDateTime now = LocalDateTime.now();

        if (performanceStartAt.isBefore(now)) {
            throw new BusinessException(PerformanceErrorCode.PERFORMANCE_SESSION_INVALID_START_TIME);
        }

        if (now.isAfter(this.performance.getTicketOpenAt())) {
            throw new BusinessException(PerformanceErrorCode.PERFORMANCE_SESSION_UPDATE_NOT_ALLOWED_AFTER_TICKET_OPEN, "변경");
        }
    }

    public void validPerformanceSessionInput(String actor, LocalDateTime performanceStartAt) {
        if (actor == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "배우");
        }

        if (performanceStartAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "공연 시작 시간");
        }
    }
}

