package com.programmers.kdt.payment.client.order;

import java.util.Optional;

// 결제가 주문에 요청하는 것들. 구현은 중립 어댑터가 제공하고, 서비스 분리 시 REST 구현으로 교체된다.
public interface OrderClient {

    Optional<OrderInfo> findOrder(Long orderId);

    StartPaymentOutcome startPayment(Long orderId);

    void cancelPaymentStart(Long orderId);

    Long getTicketId(Long orderId);
}
