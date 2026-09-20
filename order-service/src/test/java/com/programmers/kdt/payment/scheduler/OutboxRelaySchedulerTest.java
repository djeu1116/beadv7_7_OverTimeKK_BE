package com.programmers.kdt.payment.scheduler;

import com.programmers.kdt.common.contract.PaymentConfirmEvent;
import com.programmers.kdt.common.contract.PaymentFailEvent;
import com.programmers.kdt.payment.client.pay.PaymentResultEventPublisher;
import com.programmers.kdt.common.contract.CompensationCompletedEvent;
import com.programmers.kdt.payment.client.refund.RefundEventPublisher;
import com.programmers.kdt.payment.entity.outbox.OutboxEvent;
import com.programmers.kdt.payment.entity.outbox.OutboxEventStatus;
import com.programmers.kdt.payment.entity.outbox.OutboxEventType;
import com.programmers.kdt.payment.repository.OutboxEventRepository;
import com.programmers.kdt.payment.service.OutboxEventWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// 실제 DB(H2) 기준 - outbox row가 relay 몇 번을 거치며 어떻게 상태 전이하는지 확인
@DataJpaTest
class OutboxRelaySchedulerTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private final PaymentResultEventPublisher paymentResultEventPublisher = mock(PaymentResultEventPublisher.class);
    private final RefundEventPublisher refundEventPublisher = mock(RefundEventPublisher.class);
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    private OutboxRelayScheduler relay;

    @BeforeEach
    void setUp() {
        OutboxEventWriter outboxEventWriter = new OutboxEventWriter(outboxEventRepository, objectMapper);
        relay = new OutboxRelayScheduler(outboxEventRepository, paymentResultEventPublisher, refundEventPublisher, outboxEventWriter, objectMapper);
    }

    private OutboxEvent save(OutboxEventType type, Long aggregateId, Object payload) {
        String json = objectMapper.writeValueAsString(payload);
        return outboxEventRepository.save(OutboxEvent.create(type, aggregateId, json));
    }

    @Test
    @DisplayName("정상 처리되면 재발행되고 SENT로 바뀐다.")
    void dispatchSucceeds_marksSent() {
        save(OutboxEventType.PAYMENT_CONFIRMED, 1L, new PaymentConfirmEvent(10L, 1L));

        relay.relay();

        verify(paymentResultEventPublisher).publishConfirmed(new PaymentConfirmEvent(10L, 1L));
        OutboxEvent result = outboxEventRepository.findAll().get(0);
        assertThat(result.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    @DisplayName("처리 중 예외가 나면 attempts가 늘고 다음 시도 시각이 뒤로 밀린다.")
    void dispatchThrows_schedulesRetry() {
        OutboxEvent event = save(OutboxEventType.PAYMENT_CONFIRMED, 1L, new PaymentConfirmEvent(10L, 1L));
        doThrow(new RuntimeException("reserveTicket 실패")).when(paymentResultEventPublisher).publishConfirmed(any());

        relay.relay();

        OutboxEvent result = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(result.getAttempts()).isEqualTo(1);
        assertThat(result.getNextRetryAt()).isAfter(LocalDateTime.now().plusSeconds(20));
        assertThat(result.getLastError()).contains("reserveTicket 실패");
    }

    @Test
    @DisplayName("다음 시도 시각이 아직 안 지났으면 이번 틱에서는 건드리지 않는다.")
    void notYetDue_skipped() {
        OutboxEvent event = save(OutboxEventType.PAYMENT_CONFIRMED, 1L, new PaymentConfirmEvent(10L, 1L));
        event.scheduleRetry(LocalDateTime.now().plusMinutes(5), "이전 실패");
        outboxEventRepository.save(event);

        relay.relay();

        verifyNoInteractions(paymentResultEventPublisher);
    }

    @Test
    @DisplayName("PAYMENT_CONFIRMED가 재시도 5회를 다 채우면 FAILED로 확정하고, 보상 요청(COMPENSATION_REQUESTED)을 새로 등록한다.")
    void exhaustsRetries_marksFailedAndEnqueuesCompensation() {
        OutboxEvent event = save(OutboxEventType.PAYMENT_CONFIRMED, 1L, new PaymentConfirmEvent(10L, 1L));
        // 이미 4번 실패한 상태를 흉내냄(마지막 5번째 시도가 이번 relay() 호출)
        for (int i = 0; i < 4; i++) {
            event.scheduleRetry(LocalDateTime.now().minusSeconds(1), "이전 실패 " + i);
        }
        outboxEventRepository.save(event);
        doThrow(new RuntimeException("계속 실패")).when(paymentResultEventPublisher).publishConfirmed(any());

        relay.relay();

        OutboxEvent result = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(result.getLastError()).contains("계속 실패");

        List<OutboxEvent> all = outboxEventRepository.findAll();
        OutboxEvent compensation = all.stream()
                .filter(e -> e.getEventType() == OutboxEventType.COMPENSATION_REQUESTED)
                .findFirst().orElseThrow();
        assertThat(compensation.getAggregateId()).isEqualTo(1L);
        assertThat(compensation.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    @DisplayName("PAYMENT_FAILED가 재시도를 다 채워도 보상 요청은 등록하지 않는다 - 이미 실패 처리된 결제라 되돌릴 게 없음.")
    void exhaustsRetries_paymentFailed_doesNotEnqueueCompensation() {
        OutboxEvent event = save(OutboxEventType.PAYMENT_FAILED, 1L, new PaymentFailEvent(10L, 1L, "PG_REQUEST_FAILED"));
        for (int i = 0; i < 4; i++) {
            event.scheduleRetry(LocalDateTime.now().minusSeconds(1), "이전 실패 " + i);
        }
        outboxEventRepository.save(event);
        doThrow(new RuntimeException("계속 실패")).when(paymentResultEventPublisher).publishFailed(any());

        relay.relay();

        OutboxEvent result = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OutboxEventStatus.FAILED);

        boolean anyCompensation = outboxEventRepository.findAll().stream()
                .anyMatch(e -> e.getEventType() == OutboxEventType.COMPENSATION_REQUESTED);
        assertThat(anyCompensation).isFalse();
    }

    @Test
    @DisplayName("COMPENSATION_COMPLETED는 보상 완료 이벤트로 재발행되고 SENT로 바뀐다.")
    void compensationCompleted_dispatchesAndMarksSent() {
        save(OutboxEventType.COMPENSATION_COMPLETED, 1L, new CompensationCompletedEvent(10L, 1L));

        relay.relay();

        verify(refundEventPublisher).publishCompensationCompleted(new CompensationCompletedEvent(10L, 1L));
        OutboxEvent result = outboxEventRepository.findAll().get(0);
        assertThat(result.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }
}
