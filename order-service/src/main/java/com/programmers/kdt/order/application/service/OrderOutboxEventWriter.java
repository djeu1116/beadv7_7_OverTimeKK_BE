package com.programmers.kdt.order.application.service;

import com.programmers.kdt.order.domain.entity.outbox.OrderOutboxEvent;
import com.programmers.kdt.order.domain.entity.outbox.OrderOutboxEventType;
import com.programmers.kdt.order.infrastructure.repository.OrderOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// 상태 변경과 같은 트랜잭션 안에서 호출해야 함 - outbox insert가 같이 커밋/롤백돼야 원자성이 보장됨
@Component
@RequiredArgsConstructor
public class OrderOutboxEventWriter {

    private final OrderOutboxEventRepository orderOutboxEventRepository;
    private final ObjectMapper objectMapper;

    public void enqueue(OrderOutboxEventType eventType, Long aggregateId, Object payload) {
        String json = objectMapper.writeValueAsString(payload);
        orderOutboxEventRepository.save(OrderOutboxEvent.create(eventType, aggregateId, json));
    }
}
