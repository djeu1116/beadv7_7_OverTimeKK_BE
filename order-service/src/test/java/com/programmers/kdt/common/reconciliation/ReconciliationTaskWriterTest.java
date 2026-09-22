package com.programmers.kdt.common.reconciliation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 DB(H2) 기준 - 저장/조회가 실제로 동작하는지 확인
@DataJpaTest
class ReconciliationTaskWriterTest {

    @Autowired
    private ReconciliationTaskRepository reconciliationTaskRepository;

    private ReconciliationTaskWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ReconciliationTaskWriter(reconciliationTaskRepository);
    }

    @Test
    @DisplayName("record()로 남긴 항목은 OPEN 상태로 조회된다.")
    void recordSavesOpenTask() {
        writer.record(ReconciliationTaskType.PAYMENT_START_COMPENSATION_FAILED, 1L, "orderClient 타임아웃");

        var page = reconciliationTaskRepository.findByStatus(ReconciliationTaskStatus.OPEN, PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement().satisfies(task -> {
            assertThat(task.getTaskType()).isEqualTo(ReconciliationTaskType.PAYMENT_START_COMPENSATION_FAILED);
            assertThat(task.getAggregateId()).isEqualTo(1L);
            assertThat(task.getAmount()).isNull();
            assertThat(task.getDetail()).isEqualTo("orderClient 타임아웃");
        });
    }

    @Test
    @DisplayName("금액 포함 오버로드는 amount를 그대로 남긴다.")
    void recordWithAmount() {
        writer.record(ReconciliationTaskType.POINT_ROLLBACK_FAILED, 5L, 3000L, "동시성 충돌");

        var page = reconciliationTaskRepository.findByStatus(ReconciliationTaskStatus.OPEN, PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement()
                .extracting(ReconciliationTask::getAmount)
                .isEqualTo(3000L);
    }

    @Test
    @DisplayName("타입으로 좁혀서 조회할 수 있다.")
    void findByStatusAndTaskType() {
        writer.record(ReconciliationTaskType.OUTBOX_DELIVERY_FAILED, 1L, "a");
        writer.record(ReconciliationTaskType.COMPENSATION_FAILED, 2L, "b");

        var page = reconciliationTaskRepository.findByStatusAndTaskType(
                ReconciliationTaskStatus.OPEN, ReconciliationTaskType.COMPENSATION_FAILED, PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement()
                .extracting(ReconciliationTask::getAggregateId)
                .isEqualTo(2L);
    }
}
