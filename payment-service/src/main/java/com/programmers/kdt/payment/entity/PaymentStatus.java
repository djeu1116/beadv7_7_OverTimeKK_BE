package com.programmers.kdt.payment.entity;

public enum PaymentStatus {
    READY,  //PG 결제창 호출 전/중
    PAID,  //결제 승인 완료
    FAILED, //결제 승인 실패
    CONFIRM_PENDING_VERIFICATION, // PG 승인 예외
    REFUND_PENDING, // 환불 진행 중
    CANCELLED // 결제 전액 취소
}