package com.programmers.kdt.payment.infrastructure.client.refund;

import com.programmers.kdt.common.contract.RefundCompletedEvent;
import com.programmers.kdt.common.contract.RefundFailedEvent;
import com.programmers.kdt.common.contract.CompensationCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("!order-api")
@Component
@RequiredArgsConstructor
// SpringEvent를 이용한 이벤트 발행(추후에 메세지 큐로 변경해야함)
public class SpringRefundEventPublisher implements RefundEventPublisher {
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publish(RefundRequestEvent event) {
        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishCompleted(RefundCompletedEvent event) {
        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishFailed(RefundFailedEvent event) {
        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishCompensationRequested(CompensationRequestEvent event) {
        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishCompensationCompleted(CompensationCompletedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
