package com.programmers.kdt.performance.domain.entity;

import lombok.Getter;

@Getter
public enum PerformanceStatus {
    UPCOMING("공연 전"),
    BOOKING("예매 중"),
    CLOSED("공연 종료"),
    CANCELED("공연 취소");

    private final String message;

    PerformanceStatus(String message) {
        this.message = message;
    }
}