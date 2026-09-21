package com.programmers.kdt.order.entity.outbox;

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
@Table(name = "order_outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderOutboxEvent extends BaseTimeEntity {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private OrderOutboxEventType eventType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderOutboxEventStatus status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    public static OrderOutboxEvent create(OrderOutboxEventType eventType, Long aggregateId, String payload) {
        OrderOutboxEvent event = new OrderOutboxEvent();
        event.eventType = eventType;
        event.aggregateId = aggregateId;
        event.payload = payload;
        event.status = OrderOutboxEventStatus.PENDING;
        event.attempts = 0;
        event.nextRetryAt = LocalDateTime.now();
        return event;
    }

    public void markSent() {
        this.status = OrderOutboxEventStatus.SENT;
    }

    public void scheduleRetry(LocalDateTime nextRetryAt, String error) {
        this.attempts += 1;
        this.nextRetryAt = nextRetryAt;
        this.lastError = truncate(error);
    }

    public void markFailed(String error) {
        this.status = OrderOutboxEventStatus.FAILED;
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
