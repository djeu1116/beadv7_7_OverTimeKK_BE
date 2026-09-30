package com.programmers.kdt.order.infrastructure.client;

import com.programmers.kdt.common.contract.OrderCancelRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("!payment-api")
@Component
@RequiredArgsConstructor
public class SpringOrderEventPublisher implements OrderEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publishCancelRequested(OrderCancelRequestedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
