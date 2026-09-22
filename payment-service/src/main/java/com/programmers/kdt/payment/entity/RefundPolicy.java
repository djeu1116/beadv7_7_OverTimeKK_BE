package com.programmers.kdt.payment.entity;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// 정산 정책
public class RefundPolicy {
    public static double resolveRefundRate(LocalDate performanceDate, LocalDate requestDate) {
        long daysBefore = ChronoUnit.DAYS.between(requestDate, performanceDate);
        if (daysBefore >= 6) return 1.0;  // 6일 이상 전: 전액
        if (daysBefore == 5) return 0.5;
        if (daysBefore == 4) return 0.4;
        if (daysBefore == 3) return 0.3;
        if (daysBefore == 2) return 0.2;
        if (daysBefore == 1) return 0.1;
        return 0.0; // 당일 이후: 불가
    }

    // 1원 단위는 반올림하여 10원 단위로 정리
    public static Long calculateRefundAmount(Long amount, double refundRate) {
        long raw = Math.round(amount * refundRate / 10.0) * 10L;
        return raw;
    }
}
