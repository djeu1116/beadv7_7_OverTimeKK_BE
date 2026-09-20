package com.programmers.kdt.order.event;

import com.programmers.kdt.order.service.OrderService;
import com.programmers.kdt.common.contract.PaymentFailEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailEventListener {

    private final OrderService orderService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(PaymentFailEvent event){
        log.info("결제 실패 이벤트 수신: orderId={}, paymentId={}, reason={}", event.orderId(), event.paymentId(), event.reason());
        orderService.handlePaymentFailed(event.orderId());
    }

}
