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
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderOutboxEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private OrderOutboxEventType eventType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderOutboxEventStatus status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error")
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
        this.lastError = error;
    }

    public void markFailed(String error) {
        this.status = OrderOutboxEventStatus.FAILED;
        this.lastError = error;
    }
}
