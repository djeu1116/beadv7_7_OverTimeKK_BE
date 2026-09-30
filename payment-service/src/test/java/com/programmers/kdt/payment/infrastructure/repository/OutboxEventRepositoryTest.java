package com.programmers.kdt.payment.infrastructure.repository;

import com.programmers.kdt.payment.domain.entity.outbox.OutboxEvent;
import com.programmers.kdt.payment.domain.entity.outbox.OutboxEventStatus;
import com.programmers.kdt.payment.domain.entity.outbox.OutboxEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OutboxEventRepositoryTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("생성한 이벤트는 PENDING 상태로 저장되고, 상태/재시도시각 조건으로 조회된다.")
    void createAndFindPendingDue() {
        OutboxEvent event = OutboxEvent.create(OutboxEventType.PAYMENT_CONFIRMED, 1L, "{\"orderId\":1,\"paymentId\":1}");
        outboxEventRepository.save(event);

        Page<OutboxEvent> result = outboxEventRepository.findByStatusAndNextRetryAtLessThanEqual(
                OutboxEventStatus.PENDING, LocalDateTime.now().plusSeconds(1), PageRequest.of(0, 50));

        assertThat(result.getContent()).hasSize(1);
        OutboxEvent found = result.getContent().get(0);
        assertThat(found.getEventType()).isEqualTo(OutboxEventType.PAYMENT_CONFIRMED);
        assertThat(found.getAggregateId()).isEqualTo(1L);
        assertThat(found.getPayload()).isEqualTo("{\"orderId\":1,\"paymentId\":1}");
        assertThat(found.getAttempts()).isEqualTo(0);
    }

    @Test
    @DisplayName("markSent 하면 더 이상 PENDING 조회에 걸리지 않는다.")
    void markSentExcludesFromPendingQuery() {
        OutboxEvent event = OutboxEvent.create(OutboxEventType.PAYMENT_FAILED, 2L, "{}");
        event.markSent();
        outboxEventRepository.save(event);

        Page<OutboxEvent> result = outboxEventRepository.findByStatusAndNextRetryAtLessThanEqual(
                OutboxEventStatus.PENDING, LocalDateTime.now().plusSeconds(1), PageRequest.of(0, 50));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("scheduleRetry 하면 attempts가 늘고, next_retry_at 이전에는 조회되지 않는다.")
    void scheduleRetryDelaysNextPickup() {
        OutboxEvent event = OutboxEvent.create(OutboxEventType.REFUND_REQUESTED, 3L, "{}");
        outboxEventRepository.save(event);

        event.scheduleRetry(LocalDateTime.now().plusMinutes(10), "PG timeout");
        outboxEventRepository.save(event);

        Page<OutboxEvent> dueNow = outboxEventRepository.findByStatusAndNextRetryAtLessThanEqual(
                OutboxEventStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, 50));
        assertThat(dueNow.getContent()).isEmpty();

        Page<OutboxEvent> dueLater = outboxEventRepository.findByStatusAndNextRetryAtLessThanEqual(
                OutboxEventStatus.PENDING, LocalDateTime.now().plusMinutes(11), PageRequest.of(0, 50));
        assertThat(dueLater.getContent()).hasSize(1);
        assertThat(dueLater.getContent().get(0).getAttempts()).isEqualTo(1);
        assertThat(dueLater.getContent().get(0).getLastError()).isEqualTo("PG timeout");
    }
}
