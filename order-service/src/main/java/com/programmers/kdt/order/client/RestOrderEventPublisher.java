package com.programmers.kdt.order.client;

import com.programmers.kdt.common.contract.OrderCancelRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// 결제 서비스의 내부 수신 엔드포인트로 이벤트를 전달한다. 실패하면 예외를 던져 주문 outbox 릴레이가 재시도한다.
@Profile("payment-api")
@Component
@RequiredArgsConstructor
public class RestOrderEventPublisher implements OrderEventPublisher {

    private final RestClient paymentEventRestClient;

    @Override
    public void publishCancelRequested(OrderCancelRequestedEvent event) {
        paymentEventRestClient.post()
                .uri("/internal/events/order-cancel-requested")
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }
}
