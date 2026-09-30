package com.programmers.kdt.payment.domain.entity;

// Payment.paymentStatus와는 별개로 "이 시도"가 어떻게 끝났는지만 기록한다.
// REFUND_PENDING/CANCELLED처럼 결제 확정 이후의 상태는 여기 없다 - 그건 Payment(전체 결과) 몫이다.
public enum PaymentAttemptStatus {
    READY,
    PENDING_VERIFICATION,
    PAID,
    FAILED
}
