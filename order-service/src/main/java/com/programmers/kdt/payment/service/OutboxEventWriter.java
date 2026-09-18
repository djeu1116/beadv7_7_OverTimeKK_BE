package com.programmers.kdt.payment.service;

import com.programmers.kdt.payment.client.refund.CompensationRequestEvent;
import com.programmers.kdt.payment.entity.outbox.OutboxEvent;
import com.programmers.kdt.payment.entity.outbox.OutboxEventType;
import com.programmers.kdt.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

// 상태 변경과 같은 트랜잭션 안에서 호출해야 함 - outbox insert가 그 트랜잭션과 같이 커밋/롤백돼야 원자성이 보장됨
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void enqueue(OutboxEventType eventType, Long aggregateId, Object payload) {
        String json = objectMapper.writeValueAsString(payload);
        outboxEventRepository.save(OutboxEvent.create(eventType, aggregateId, json));
    }

    // 재시도 소진된 이벤트를 FAILED로 확정하는 것과, 필요하면 보상 이벤트를 새로 등록하는 것을
    // 한 트랜잭션으로 묶음 - 따로 저장하면 중간에 크래시났을 때 "FAILED로 끝났는데 보상은 안 걸림"이 생김.
    // relay가 호출하는데 relay 쪽엔 트랜잭션이 없어서, 여기 REQUIRES_NEW로 직접 경계를 만듦
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void giveUp(OutboxEvent event, String error) {
        event.markFailed(error);
        outboxEventRepository.save(event);

        if (event.getEventType() == OutboxEventType.PAYMENT_CONFIRMED) {
            enqueue(OutboxEventType.COMPENSATION_REQUESTED, event.getAggregateId(),
                    new CompensationRequestEvent(event.getAggregateId()));
        }
    }
}
