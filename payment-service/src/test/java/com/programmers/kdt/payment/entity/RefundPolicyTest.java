package com.programmers.kdt.payment.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RefundPolicyTest {

    @ParameterizedTest(name = "공연 {0}일 전 환불 요청이면 환불율은 {1}")
    @CsvSource({
            "10, 1.0",
            "6,  1.0",
            "5,  0.5",
            "4,  0.4",
            "3,  0.3",
            "2,  0.2",
            "1,  0.1",
            "0,  0.0",
            "-1, 0.0"
    })
    @DisplayName("공연일까지 남은 일수에 따라 환불율이 정해진다")
    void resolveRefundRate(long daysBefore, double expected) {
        LocalDate requestDate = LocalDate.of(2026, 8, 1);
        LocalDate performanceDate = requestDate.plusDays(daysBefore);

        assertThat(RefundPolicy.resolveRefundRate(performanceDate, requestDate))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}원의 {1} 환불은 {2}원")
    @CsvSource({
            "10000, 0.4,  4000",
            "9999,  0.35, 3500",
            "9999,  0.1,  1000",
            "1,     1.0,  0",
            "10000, 0.0,  0",
            "100,   0.15, 20"
    })
    @DisplayName("환불 금액은 10원 단위로 반올림된다")
    void calculateRefundAmount(long amount, double rate, long expected) {

        assertThat(RefundPolicy.calculateRefundAmount(amount, rate)).isEqualTo(expected);
    }

    @Test
    @DisplayName("10원 단위 반올림 결과가 원금을 넘으면 원금으로 clamp한다(L-2) - 안 그러면 Toss가 초과 환불로 거절해 환불이 영구히 막힘.")
    void calculateRefundAmount_roundingNeverExceedsPrincipal() {
        // 9997 * 1.0 / 10 = 999.7 -> 반올림하면 1000 -> *10 = 10000 (원금 9997을 3원 초과)
        assertThat(RefundPolicy.calculateRefundAmount(9997L, 1.0)).isEqualTo(9997L);
    }

    @Test
    @DisplayName("반올림 결과가 원금 이하면 그대로 반올림된 값을 쓴다(clamp가 정상 케이스에 영향 안 줌).")
    void calculateRefundAmount_belowPrincipal_notClamped() {
        assertThat(RefundPolicy.calculateRefundAmount(9990L, 1.0)).isEqualTo(9990L);
    }
}