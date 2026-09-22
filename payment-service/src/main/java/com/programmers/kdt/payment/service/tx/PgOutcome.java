package com.programmers.kdt.payment.service.tx;

public enum PgOutcome {
    SUCCESS, // 성공
    EXPLICIT_FAIL, // 완전히 실패
    AMBIGUOUS // 타임아웃 또는 예외로 인한 실패
}
