package com.programmers.kdt.payment.entity.outbox;

public enum OutboxEventType {
    PAYMENT_CONFIRMED,
    PAYMENT_FAILED,
    REFUND_REQUESTED,
    REFUND_COMPLETED,
    REFUND_FAILED,
    COMPENSATION_REQUESTED
}
