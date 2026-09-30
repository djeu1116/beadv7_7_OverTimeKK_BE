package com.programmers.kdt.order.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketCancelJobTest {

    @Test
    @DisplayName("신규 작업은 PENDING 상태와 0회 시도 횟수로 생성된다")
    void create() {
        TicketCancelJob job = TicketCancelJob.create(1L, 10L, 100L);

        assertThat(job.getOrderId()).isEqualTo(1L);
        assertThat(job.getTicketId()).isEqualTo(10L);
        assertThat(job.getUserId()).isEqualTo(100L);
        assertThat(job.getStatus()).isEqualTo(TicketCancelJobStatus.PENDING);
        assertThat(job.getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("실패를 기록하면 시도 횟수와 오류가 저장되고 PENDING을 유지한다")
    void recordFailure() {
        TicketCancelJob job = TicketCancelJob.create(1L, 10L, 100L);

        job.recordFailure("TICKET_RELEASE_FAILED");

        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getLastError()).isEqualTo("TICKET_RELEASE_FAILED");
        assertThat(job.getStatus()).isEqualTo(TicketCancelJobStatus.PENDING);
        assertThat(job.getDoneAt()).isNull();
    }

    @Test
    @DisplayName("성공 처리하면 DONE으로 전환하고 오류를 제거한다")
    void markDone() {
        TicketCancelJob job = TicketCancelJob.create(1L, 10L, 100L);
        job.recordFailure("TICKET_RELEASE_FAILED");

        job.markDone();

        assertThat(job.getStatus()).isEqualTo(TicketCancelJobStatus.DONE);
        assertThat(job.getLastError()).isNull();
        assertThat(job.getDoneAt()).isNotNull();
    }
}
