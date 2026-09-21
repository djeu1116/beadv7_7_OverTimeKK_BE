package com.programmers.kdt.order.scheduler;

import com.programmers.kdt.common.contract.OrderCancelRequestedEvent;
import com.programmers.kdt.order.entity.outbox.OrderOutboxEvent;
import com.programmers.kdt.order.entity.outbox.OrderOutboxEventStatus;
import com.programmers.kdt.order.entity.outbox.OrderOutboxEventType;
import com.programmers.kdt.order.repository.OrderOutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import com.programmers.kdt.order.client.OrderEventPublisher;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// 실제 DB(H2) 기준 - 주문 outbox row가 relay를 거치며 어떻게 상태 전이하는지 확인
@DataJpaTest
class OrderOutboxRelaySchedulerTest {

    @Autowired
    private OrderOutboxEventRepository orderOutboxEventRepository;

    private final OrderEventPublisher orderEventPublisher = mock(OrderEventPublisher.class);
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    private OrderOutboxRelayScheduler relay;

    @BeforeEach
    void setUp() {
        relay = new OrderOutboxRelayScheduler(orderOutboxEventRepository, orderEventPublisher, objectMapper);
    }

    private OrderOutboxEvent save(Object payload) {
        String json = objectMapper.writeValueAsString(payload);
        return orderOutboxEventRepository.save(
                OrderOutboxEvent.create(OrderOutboxEventType.ORDER_CANCEL_REQUESTED, 1L, json));
    }

    @Test
    @DisplayName("주문 취소 접수 이벤트가 발행되고 SENT로 바뀐다.")
    void dispatchSucceeds_marksSent() {
        save(new OrderCancelRequestedEvent(1L, "단순 변심"));

        relay.relay();

        verify(orderEventPublisher).publishCancelRequested(new OrderCancelRequestedEvent(1L, "단순 변심"));
        assertThat(orderOutboxEventRepository.findAll().get(0).getStatus()).isEqualTo(OrderOutboxEventStatus.SENT);
    }

    @Test
    @DisplayName("소비자가 예외를 던지면 attempts가 늘고 다음 시도 시각이 뒤로 밀린다.")
    void dispatchThrows_schedulesRetry() {
        OrderOutboxEvent event = save(new OrderCancelRequestedEvent(1L, "단순 변심"));
        doThrow(new RuntimeException("환불 접수 실패")).when(orderEventPublisher).publishCancelRequested(any());

        relay.relay();

        OrderOutboxEvent result = orderOutboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OrderOutboxEventStatus.PENDING);
        assertThat(result.getAttempts()).isEqualTo(1);
        assertThat(result.getNextRetryAt()).isAfter(LocalDateTime.now().plusSeconds(20));
        assertThat(result.getLastError()).contains("환불 접수 실패");
    }

    @Test
    @DisplayName("재시도 5회를 다 채우면 FAILED로 확정한다.")
    void exhaustsRetries_marksFailed() {
        OrderOutboxEvent event = save(new OrderCancelRequestedEvent(1L, "단순 변심"));
        for (int i = 0; i < 4; i++) {
            event.scheduleRetry(LocalDateTime.now().minusSeconds(1), "이전 실패 " + i);
        }
        orderOutboxEventRepository.save(event);
        doThrow(new RuntimeException("계속 실패")).when(orderEventPublisher).publishCancelRequested(any());

        relay.relay();

        assertThat(orderOutboxEventRepository.findById(event.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderOutboxEventStatus.FAILED);
    }

    @Test
    @DisplayName("다음 시도 시각이 아직 안 지났으면 이번 틱에서는 건드리지 않는다.")
    void notYetDue_skipped() {
        OrderOutboxEvent event = save(new OrderCancelRequestedEvent(1L, "단순 변심"));
        event.scheduleRetry(LocalDateTime.now().plusMinutes(5), "이전 실패");
        orderOutboxEventRepository.save(event);

        relay.relay();

        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    @DisplayName("오류 메시지가 컬럼 길이를 넘어도 잘라서 저장하고 재시도 기록이 실패하지 않는다 - HTTP 오류 응답 본문이 길 수 있음.")
    void longErrorMessage_isTruncatedAndRetryStillRecorded() {
        OrderOutboxEvent event = save(new OrderCancelRequestedEvent(1L, "단순 변심"));
        doThrow(new RuntimeException("x".repeat(2000))).when(orderOutboxEventPublisherMock()).publishCancelRequested(any());

        relay.relay();

        OrderOutboxEvent result = orderOutboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(result.getAttempts()).isEqualTo(1);
        assertThat(result.getLastError()).hasSizeLessThanOrEqualTo(500);
    }

    private OrderEventPublisher orderOutboxEventPublisherMock() {
        return orderEventPublisher;
    }
}
