package com.programmers.kdt.order.domain.entity;

import com.programmers.kdt.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ticket_cancel_job",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ticket_cancel_job_order_id", columnNames = "order_id")
        },
        indexes = {
                @Index(name = "idx_ticket_cancel_job_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketCancelJob extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketCancelJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 255)
    private String lastError;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    public static TicketCancelJob create(Long orderId, Long ticketId, Long userId) {
        TicketCancelJob job = new TicketCancelJob();
        job.orderId = orderId;
        job.ticketId = ticketId;
        job.userId = userId;
        job.status = TicketCancelJobStatus.PENDING;
        job.attemptCount = 0;
        return job;
    }

    public void markDone() {
        if (status == TicketCancelJobStatus.DONE) {
            return;
        }
        this.status = TicketCancelJobStatus.DONE;
        this.lastError = null;
        this.doneAt = LocalDateTime.now();
    }

    public void recordFailure(String error) {
        this.attemptCount++;
        this.lastError = error;
        this.status = TicketCancelJobStatus.PENDING;
        this.doneAt = null;
    }
}
