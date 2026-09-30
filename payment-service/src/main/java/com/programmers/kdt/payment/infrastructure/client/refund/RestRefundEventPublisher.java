package com.programmers.kdt.payment.infrastructure.client.refund;

import com.programmers.kdt.common.contract.CompensationCompletedEvent;
import com.programmers.kdt.common.contract.RefundCompletedEvent;
import com.programmers.kdt.common.contract.RefundFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// 환불 이벤트 중 주문이 받는 결과 3종은 HTTP로 전달하고, 결제 안에서만 도는 요청 2종은 그대로 프로세스 안에서 발행한다.
@Profile("order-api")
@Component
@RequiredArgsConstructor
public class RestRefundEventPublisher implements RefundEventPublisher {

    private final RestClient orderEventRestClient;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publish(RefundRequestEvent event) {
        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishCompensationRequested(CompensationRequestEvent event) {
        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishCompleted(RefundCompletedEvent event) {
        post("/internal/events/refund-completed", event);
    }

    @Override
    public void publishFailed(RefundFailedEvent event) {
        post("/internal/events/refund-failed", event);
    }

    @Override
    public void publishCompensationCompleted(CompensationCompletedEvent event) {
        post("/internal/events/compensation-completed", event);
    }

    private void post(String uri, Object event) {
        orderEventRestClient.post()
                .uri(uri)
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }
}
