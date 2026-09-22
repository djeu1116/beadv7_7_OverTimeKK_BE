package com.programmers.kdt.payment.controller.internal;

import com.programmers.kdt.common.contract.OrderCancelRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 주문 서비스가 보내는 이벤트를 받는 내부 엔드포인트. 소비자는 멱등이라 같은 이벤트가 다시 와도 안전하다.
// 트랜잭션 없이 발행하므로 리스너가 동기로 실행되고, 실패하면 예외가 HTTP 오류로 전달되어 발신 측이 재시도한다.
@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class PaymentEventInternalController {

    private final ApplicationEventPublisher eventPublisher;

    @PostMapping("/order-cancel-requested")
    public void orderCancelRequested(@RequestBody OrderCancelRequestedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
