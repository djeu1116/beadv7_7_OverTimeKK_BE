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

    // 1원 단위는 반올림하여 10원 단위로 정리. amount가 10원 단위가 아니면(예: 포인트를 3원 사용해서
    // PG 결제분이 9997원 같은 값이 되는 경우) 반올림이 원금을 넘어설 수 있어(9997원의 100% 환불이
    // 10000원으로 반올림) Toss가 "환불액이 결제액 초과"로 거절해 환불이 영구히 막힐 수 있었다(L-2).
    // 반올림 결과가 원금(amount)을 넘지 않도록 상한을 둔다.
    public static Long calculateRefundAmount(Long amount, double refundRate) {
        long raw = Math.round(amount * refundRate / 10.0) * 10L;
        return Math.min(raw, amount);
    }
}
