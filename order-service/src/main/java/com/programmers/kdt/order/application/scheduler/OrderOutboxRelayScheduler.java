package com.programmers.kdt.order.application.scheduler;

import com.programmers.kdt.common.contract.OrderCancelRequestedEvent;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskType;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskWriter;
import com.programmers.kdt.order.infrastructure.client.OrderEventPublisher;
import com.programmers.kdt.order.domain.entity.outbox.OrderOutboxEvent;
import com.programmers.kdt.order.domain.entity.outbox.OrderOutboxEventStatus;
import com.programmers.kdt.order.infrastructure.repository.OrderOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;

// 주문 outbox 릴레이. 결제 쪽 릴레이와 같은 패턴이며, dispatch를 트랜잭션 없이 호출해야
// @TransactionalEventListener(fallbackExecution=true) 소비자가 동기로 실행되고 결과를 여기서 받을 수 있음
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxRelayScheduler {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(30);

    private final OrderOutboxEventRepository orderOutboxEventRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final ObjectMapper objectMapper;
    private final ReconciliationTaskWriter reconciliationTaskWriter;

    @Scheduled(fixedDelay = 10000)
    @SchedulerLock(name = "orderOutboxRelay", lockAtMostFor = "2m", lockAtLeastFor = "10s")
    public void relay() {
        Page<OrderOutboxEvent> due = orderOutboxEventRepository.findByStatusAndNextRetryAtLessThanEqual(
                OrderOutboxEventStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE, Sort.by("id").ascending()));
        if (due.isEmpty()) return;

        int sent = 0;
        for (OrderOutboxEvent event : due) {
            if (process(event)) sent++;
        }
        log.info("주문 outbox 이벤트 {}건 중 {}건 처리", due.getNumberOfElements(), sent);
    }

    private boolean process(OrderOutboxEvent event) {
        try {
            dispatch(event);
            event.markSent();
            orderOutboxEventRepository.save(event);
            return true;
        } catch (Exception e) {
            log.warn("주문 outbox 이벤트 처리 실패 - id={}, type={}, aggregateId={}",
                    event.getId(), event.getEventType(), event.getAggregateId(), e);
            if (event.getAttempts() + 1 >= MAX_ATTEMPTS) {
                event.markFailed(e.getMessage());
                orderOutboxEventRepository.save(event);
                log.error("[ORDER_OUTBOX_RECONCILIATION_NEEDED] 재시도 소진 - id={}, type={}, aggregateId={}",
                        event.getId(), event.getEventType(), event.getAggregateId());
                reconciliationTaskWriter.record(ReconciliationTaskType.ORDER_OUTBOX_DELIVERY_FAILED, event.getAggregateId(),
                        "eventType=" + event.getEventType() + ", outboxId=" + event.getId() + ", error=" + e.getMessage());
            } else {
                event.scheduleRetry(LocalDateTime.now().plus(RETRY_BACKOFF), e.getMessage());
                orderOutboxEventRepository.save(event);
            }
            return false;
        }
    }

    private void dispatch(OrderOutboxEvent event) {
        switch (event.getEventType()) {
            case ORDER_CANCEL_REQUESTED ->
                    orderEventPublisher.publishCancelRequested(objectMapper.readValue(event.getPayload(), OrderCancelRequestedEvent.class));
        }
    }
}
