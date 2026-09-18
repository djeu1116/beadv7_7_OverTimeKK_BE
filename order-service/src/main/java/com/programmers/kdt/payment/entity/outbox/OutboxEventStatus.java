package com.programmers.kdt.payment.entity.outbox;

public enum OutboxEventStatus {
    PENDING, // 처리 대기(또는 재시도 대기)
    SENT, // 처리 완료
    FAILED // 재시도 소진, 수동 개입 필요
}
