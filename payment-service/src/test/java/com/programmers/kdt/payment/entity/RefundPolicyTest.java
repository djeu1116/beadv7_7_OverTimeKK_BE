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
}