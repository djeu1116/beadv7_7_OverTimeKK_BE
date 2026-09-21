package com.programmers.kdt.payment.client.pay;

import com.programmers.kdt.common.contract.PaymentConfirmEvent;
import com.programmers.kdt.common.contract.PaymentFailEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// 주문 서비스의 내부 수신 엔드포인트로 이벤트를 전달한다. 실패하면 예외를 던져 outbox 릴레이가 재시도한다.
@Profile("order-api")
@Component
@RequiredArgsConstructor
public class RestPaymentResultEventPublisher implements PaymentResultEventPublisher {

    private final RestClient orderEventRestClient;

    @Override
    public void publishConfirmed(PaymentConfirmEvent event) {
        orderEventRestClient.post()
                .uri("/internal/events/payment-confirmed")
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void publishFailed(PaymentFailEvent event) {
        orderEventRestClient.post()
                .uri("/internal/events/payment-failed")
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }
}
