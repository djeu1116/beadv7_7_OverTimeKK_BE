package com.programmers.kdt.common.reconciliation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationTaskTest {

    @Test
    @DisplayName("생성하면 OPEN 상태이고 해소 시각은 비어있다.")
    void create() {
        ReconciliationTask task = ReconciliationTask.create(ReconciliationTaskType.COMPENSATION_FAILED, 1L, 10000L, "detail");

        assertThat(task.getStatus()).isEqualTo(ReconciliationTaskStatus.OPEN);
        assertThat(task.getAggregateId()).isEqualTo(1L);
        assertThat(task.getAmount()).isEqualTo(10000L);
        assertThat(task.getDetail()).isEqualTo("detail");
        assertThat(task.getResolvedAt()).isNull();
    }

    @Test
    @DisplayName("resolve()하면 RESOLVED로 바뀌고 해소 시각이 기록된다.")
    void resolve() {
        ReconciliationTask task = ReconciliationTask.create(ReconciliationTaskType.COMPENSATION_FAILED, 1L, null, null);

        task.resolve();

        assertThat(task.getStatus()).isEqualTo(ReconciliationTaskStatus.RESOLVED);
        assertThat(task.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 RESOLVED면 다시 호출해도 해소 시각이 바뀌지 않는다 - 중복 처리 대비.")
    void resolveIsIdempotent() {
        ReconciliationTask task = ReconciliationTask.create(ReconciliationTaskType.COMPENSATION_FAILED, 1L, null, null);
        task.resolve();
        var firstResolvedAt = task.getResolvedAt();

        task.resolve();

        assertThat(task.getResolvedAt()).isEqualTo(firstResolvedAt);
    }

    @Test
    @DisplayName("상세 메시지가 500자를 넘으면 잘라서 저장한다 - 컬럼 길이 초과로 저장 자체가 실패하는 것을 막기 위함.")
    void detailIsTruncated() {
        ReconciliationTask task = ReconciliationTask.create(ReconciliationTaskType.COMPENSATION_FAILED, 1L, null, "x".repeat(2000));

        assertThat(task.getDetail()).hasSize(500);
    }
}
