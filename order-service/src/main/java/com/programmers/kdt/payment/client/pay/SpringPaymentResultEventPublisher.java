package com.programmers.kdt.payment.client.pay;

import com.programmers.kdt.common.contract.PaymentConfirmEvent;
import com.programmers.kdt.common.contract.PaymentFailEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringPaymentResultEventPublisher implements PaymentResultEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publishConfirmed(PaymentConfirmEvent event) {
        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishFailed(PaymentFailEvent event) {
        eventPublisher.publishEvent(event);
    }
}

