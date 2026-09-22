package com.programmers.kdt.payment.entity;

public enum PointType {
    EARN,  // 포인트 적립
    USE, // 포인트 사용
    CANCELLED, // 포인트 사용 취소
    PARTIAL_CANCELLED, // 포인트 사용 부분 취소
}
