package com.programmers.kdt.order.client;

import com.programmers.kdt.common.contract.OrderCancelRequestedEvent;

// 주문이 결제 컨텍스트로 내보내는 이벤트. 구현은 프로세스 안 발행 또는 HTTP 전송 중 하나다.
public interface OrderEventPublisher {

    void publishCancelRequested(OrderCancelRequestedEvent event);
}
