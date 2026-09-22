package com.programmers.kdt.payment.scheduler;

import com.programmers.kdt.common.contract.PaymentConfirmEvent;
import com.programmers.kdt.common.contract.PaymentFailEvent;
import com.programmers.kdt.payment.client.pay.PaymentResultEventPublisher;
import com.programmers.kdt.common.contract.CompensationCompletedEvent;
import com.programmers.kdt.payment.client.refund.CompensationRequestEvent;
import com.programmers.kdt.common.contract.RefundCompletedEvent;
import com.programmers.kdt.payment.client.refund.RefundEventPublisher;
import com.programmers.kdt.common.contract.RefundFailedEvent;
import com.programmers.kdt.payment.client.refund.RefundRequestEvent;
import com.programmers.kdt.payment.entity.outbox.OutboxEvent;
import com.programmers.kdt.payment.entity.outbox.OutboxEventStatus;
import com.programmers.kdt.payment.repository.OutboxEventRepository;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskType;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskWriter;
import com.programmers.kdt.payment.service.OutboxEventWriter;
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

// outbox에 쌓인 이벤트를 꺼내서 재발행하는 릴레이. dispatch()는 트랜잭션 없이 호출해야
// @TransactionalEventListener(fallbackExecution=true)가 즉시·동기로 실행되고, 그 결과(성공/예외)를
// 여기서 바로 받아서 재시도 여부를 정할 수 있음
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(30);

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentResultEventPublisher paymentResultEventPublisher;
    private final RefundEventPublisher refundEventPublisher;
    private final OutboxEventWriter outboxEventWriter;
    private final ObjectMapper objectMapper;
    private final ReconciliationTaskWriter reconciliationTaskWriter;

    @Scheduled(fixedDelay = 10000)
    @SchedulerLock(name = "outboxRelay", lockAtMostFor = "2m", lockAtLeastFor = "10s")
    public void relay() {
        Page<OutboxEvent> due = outboxEventRepository.findByStatusAndNextRetryAtLessThanEqual(
                OutboxEventStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE, Sort.by("id").ascending()));
        if (due.isEmpty()) return;

        int sent = 0;
        for (OutboxEvent event : due) {
            if (process(event)) sent++;
        }
        log.info("outbox 이벤트 {}건 중 {}건 처리", due.getNumberOfElements(), sent);
    }

    private boolean process(OutboxEvent event) {
        try {
            dispatch(event);
            event.markSent();
            outboxEventRepository.save(event);
            return true;
        } catch (Exception e) {
            log.warn("outbox 이벤트 처리 실패 - id={}, type={}, aggregateId={}",
                    event.getId(), event.getEventType(), event.getAggregateId(), e);
            if (event.getAttempts() + 1 >= MAX_ATTEMPTS) {
                outboxEventWriter.giveUp(event, e.getMessage());
                log.error("[OUTBOX_RECONCILIATION_NEEDED] 재시도 소진 - id={}, type={}, aggregateId={}",
                        event.getId(), event.getEventType(), event.getAggregateId());
                reconciliationTaskWriter.record(ReconciliationTaskType.OUTBOX_DELIVERY_FAILED, event.getAggregateId(),
                        "eventType=" + event.getEventType() + ", outboxId=" + event.getId() + ", error=" + e.getMessage());
            } else {
                event.scheduleRetry(LocalDateTime.now().plus(RETRY_BACKOFF), e.getMessage());
                outboxEventRepository.save(event);
            }
            return false;
        }
    }

    private void dispatch(OutboxEvent event) {
        switch (event.getEventType()) {
            case PAYMENT_CONFIRMED -> paymentResultEventPublisher.publishConfirmed(deserialize(event, PaymentConfirmEvent.class));
            case PAYMENT_FAILED -> paymentResultEventPublisher.publishFailed(deserialize(event, PaymentFailEvent.class));
            case REFUND_REQUESTED -> refundEventPublisher.publish(deserialize(event, RefundRequestEvent.class));
            case REFUND_COMPLETED -> refundEventPublisher.publishCompleted(deserialize(event, RefundCompletedEvent.class));
            case REFUND_FAILED -> refundEventPublisher.publishFailed(deserialize(event, RefundFailedEvent.class));
            case COMPENSATION_REQUESTED -> refundEventPublisher.publishCompensationRequested(deserialize(event, CompensationRequestEvent.class));
            case COMPENSATION_COMPLETED -> refundEventPublisher.publishCompensationCompleted(deserialize(event, CompensationCompletedEvent.class));
        }
    }

    private <T> T deserialize(OutboxEvent event, Class<T> type) {
        return objectMapper.readValue(event.getPayload(), type);
    }
}
