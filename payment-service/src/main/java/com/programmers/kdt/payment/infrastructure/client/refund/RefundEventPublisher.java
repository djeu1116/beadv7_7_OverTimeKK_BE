package com.programmers.kdt.payment.infrastructure.client.refund;

import com.programmers.kdt.common.contract.RefundCompletedEvent;
import com.programmers.kdt.common.contract.RefundFailedEvent;
import com.programmers.kdt.common.contract.CompensationCompletedEvent;
public interface RefundEventPublisher { // PaymentService는 해당 인터페이스만을 의존

    void publish(RefundRequestEvent event);

    void publishCompleted(RefundCompletedEvent event);

    void publishFailed(RefundFailedEvent event);

    void publishCompensationRequested(CompensationRequestEvent event);

    void publishCompensationCompleted(CompensationCompletedEvent event);
}
