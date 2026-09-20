package com.programmers.kdt.adapter;

import com.programmers.kdt.order.api.OrderPaymentApi;
import com.programmers.kdt.payment.client.order.OrderClient;
import com.programmers.kdt.payment.client.order.OrderInfo;
import com.programmers.kdt.payment.client.order.StartPaymentOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

// 같은 프로세스에 있는 동안 결제의 out 포트를 주문의 in 포트로 잇는 임시 다리.
// 서비스를 분리하면 이 클래스는 사라지고 REST 어댑터가 대신한다.
@Profile("!order-api")
@Component
@RequiredArgsConstructor
public class LocalOrderClient implements OrderClient {

    private final OrderPaymentApi orderPaymentApi;

    @Override
    public Optional<OrderInfo> findOrder(Long orderId) {
        return orderPaymentApi.findOrder(orderId)
                .map(snapshot -> new OrderInfo(snapshot.orderId(), snapshot.userId(), snapshot.totalAmount()));
    }

    @Override
    public StartPaymentOutcome startPayment(Long orderId) {
        return switch (orderPaymentApi.startPayment(orderId)) {
            case STARTED -> StartPaymentOutcome.STARTED;
            case NOT_FOUND -> StartPaymentOutcome.NOT_FOUND;
            case NOT_PENDING -> StartPaymentOutcome.NOT_PENDING;
            case EXPIRED -> StartPaymentOutcome.EXPIRED;
        };
    }

    @Override
    public void cancelPaymentStart(Long orderId) {
        orderPaymentApi.cancelPaymentStart(orderId);
    }

    @Override
    public Long getTicketId(Long orderId) {
        return orderPaymentApi.findTicketId(orderId);
    }
}
