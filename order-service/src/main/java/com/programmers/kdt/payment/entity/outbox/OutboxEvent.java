package com.programmers.kdt.payment.entity.outbox;

import com.programmers.kdt.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseTimeEntity {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private OutboxEventType eventType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload; // 이벤트 DTO를 JSON으로 직렬화한 값

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxEventStatus status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    public static OutboxEvent create(OutboxEventType eventType, Long aggregateId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.eventType = eventType;
        event.aggregateId = aggregateId;
        event.payload = payload;
        event.status = OutboxEventStatus.PENDING;
        event.attempts = 0;
        event.nextRetryAt = LocalDateTime.now();
        return event;
    }

    // relay가 처리를 성공시켰을 때
    public void markSent() {
        this.status = OutboxEventStatus.SENT;
    }

    // relay가 처리에 실패했을 때 - 다음 시도 시각을 늦춰서 재시도
    public void scheduleRetry(LocalDateTime nextRetryAt, String error) {
        this.attempts += 1;
        this.nextRetryAt = nextRetryAt;
        this.lastError = truncate(error);
    }

    // relay가 재시도 횟수를 다 써서 포기했을 때
    public void markFailed(String error) {
        this.status = OutboxEventStatus.FAILED;
        this.lastError = truncate(error);
    }

    // 예외 메시지(특히 HTTP 오류 응답 본문)가 컬럼 길이를 넘으면 재시도 기록 자체가 실패해서 릴레이가 멈춘다
    private static String truncate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }
}
