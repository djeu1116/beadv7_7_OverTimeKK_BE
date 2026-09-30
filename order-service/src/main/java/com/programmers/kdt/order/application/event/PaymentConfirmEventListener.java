package com.programmers.kdt.order.application.event;

import com.programmers.kdt.order.application.service.OrderService;
import com.programmers.kdt.common.contract.PaymentConfirmEvent;
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
public class PaymentConfirmEventListener {

    private final OrderService orderService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(PaymentConfirmEvent event){
        log.info("결제 완료 이벤트 수신: orderId={}", event.orderId());
        orderService.completeOrder(event.orderId());
    }
}
