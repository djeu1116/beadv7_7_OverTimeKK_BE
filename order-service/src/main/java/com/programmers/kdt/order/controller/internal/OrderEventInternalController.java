package com.programmers.kdt.order.controller.internal;

import com.programmers.kdt.common.contract.CompensationCompletedEvent;
import com.programmers.kdt.common.contract.PaymentConfirmEvent;
import com.programmers.kdt.common.contract.PaymentFailEvent;
import com.programmers.kdt.common.contract.RefundCompletedEvent;
import com.programmers.kdt.common.contract.RefundFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 결제 서비스가 보내는 이벤트를 받는 내부 엔드포인트. 소비자는 멱등이라 같은 이벤트가 다시 와도 안전하다.
// 트랜잭션 없이 발행하므로 리스너가 동기로 실행되고, 실패하면 예외가 HTTP 오류로 전달되어 발신 측이 재시도한다.
@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class OrderEventInternalController {

    private final ApplicationEventPublisher eventPublisher;

    @PostMapping("/payment-confirmed")
    public void paymentConfirmed(@RequestBody PaymentConfirmEvent event) {
        eventPublisher.publishEvent(event);
    }

    @PostMapping("/payment-failed")
    public void paymentFailed(@RequestBody PaymentFailEvent event) {
        eventPublisher.publishEvent(event);
    }

    @PostMapping("/refund-completed")
    public void refundCompleted(@RequestBody RefundCompletedEvent event) {
        eventPublisher.publishEvent(event);
    }

    @PostMapping("/refund-failed")
    public void refundFailed(@RequestBody RefundFailedEvent event) {
        eventPublisher.publishEvent(event);
    }

    @PostMapping("/compensation-completed")
    public void compensationCompleted(@RequestBody CompensationCompletedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
