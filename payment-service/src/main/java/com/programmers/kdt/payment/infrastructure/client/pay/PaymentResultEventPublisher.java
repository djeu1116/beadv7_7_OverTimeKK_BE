package com.programmers.kdt.payment.infrastructure.client.pay;

import com.programmers.kdt.common.contract.PaymentConfirmEvent;
import com.programmers.kdt.common.contract.PaymentFailEvent;
public interface PaymentResultEventPublisher {
    void publishConfirmed(PaymentConfirmEvent event);
    void publishFailed(PaymentFailEvent event);
}
