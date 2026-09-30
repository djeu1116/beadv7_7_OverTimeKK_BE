package com.programmers.kdt.order.application.event;

import com.programmers.kdt.order.application.service.OrderService;
import com.programmers.kdt.common.contract.CompensationCompletedEvent;
import com.programmers.kdt.common.contract.RefundCompletedEvent;
import com.programmers.kdt.common.contract.RefundFailedEvent;
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
public class RefundResultOrderListener {

    private final OrderService orderService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleCompleted(RefundCompletedEvent event) {
        log.info("환불 완료 이벤트 수신: orderId={}, paymentId={}", event.orderId(), event.paymentId());
        orderService.confirmCancellation(event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleCompensated(CompensationCompletedEvent event) {
        log.info("보상 완료 이벤트 수신: orderId={}, paymentId={}", event.orderId(), event.paymentId());
        orderService.failOrderAfterCompensation(event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailed(RefundFailedEvent event) {
        log.info(
                "환불 실패 이벤트 수신: orderId={}, paymentId={}, reason={}",
                event.orderId(),
                event.paymentId(),
                event.reason()
        );
        orderService.revertCancellation(event.orderId());
    }
}
