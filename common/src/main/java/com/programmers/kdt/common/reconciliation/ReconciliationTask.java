package com.programmers.kdt.common.reconciliation;

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
@Table(name = "reconciliation_task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationTask extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 50)
    private ReconciliationTaskType taskType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "detail", length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconciliationTaskStatus status;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public static ReconciliationTask create(ReconciliationTaskType taskType, Long aggregateId, Long amount, String detail) {
        ReconciliationTask task = new ReconciliationTask();
        task.taskType = taskType;
        task.aggregateId = aggregateId;
        task.amount = amount;
        task.detail = truncate(detail);
        task.status = ReconciliationTaskStatus.OPEN;
        return task;
    }

    public void resolve() {
        if (status == ReconciliationTaskStatus.RESOLVED) {
            return;
        }
        this.status = ReconciliationTaskStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }

    private static String truncate(String detail) {
        if (detail == null || detail.length() <= 500) {
            return detail;
        }
        return detail.substring(0, 500);
    }
}
