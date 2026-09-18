package com.programmers.kdt.payment.client.refund;

public interface RefundEventPublisher { // PaymentService는 해당 인터페이스만을 의존

    void publish(RefundRequestEvent event);

    void publishCompleted(RefundCompletedEvent event);

    void publishFailed(RefundFailedEvent event);

    void publishCompensationRequested(CompensationRequestEvent event);
}
