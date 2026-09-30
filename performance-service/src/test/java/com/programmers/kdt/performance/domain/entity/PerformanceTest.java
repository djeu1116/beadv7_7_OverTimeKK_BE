package com.programmers.kdt.performance.domain.entity;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.performance.domain.exception.PerformanceErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PerformanceTest {

    @Test
    void 공연_생성_성공() {
        Performance performance = Performance.createPerformance(
                "뮤지컬A", "설명", 120L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                LocalDateTime.of(2026, 7, 1, 10, 0),
                1L, 1L, null);

        assertThat(performance.getTitle()).isEqualTo("뮤지컬A");
        assertThat(performance.getSellerId()).isEqualTo(1L);
        assertThat(performance.getHallId()).isEqualTo(1L);
    }

    @Test
    void 종료일이_시작일보다_빠르면_예외() {
        assertThatThrownBy(() -> Performance.createPerformance(
                "뮤지컬A", "설명", 120L,
                LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1),
                null, 1L, 1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PerformanceErrorCode.INVALID_PERIOD.getMessage());
    }

    @Test
    void 티켓오픈이_공연시작_이후면_예외() {
        assertThatThrownBy(() -> Performance.createPerformance(
                "뮤지컬A", "설명", 120L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                LocalDateTime.of(2026, 9, 1, 10, 0),
                1L, 1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PerformanceErrorCode.INVALID_TICKET_OPEN.getMessage());
    }

    @Test
    void 공연_수정_성공() {
        Performance performance = Performance.createPerformance(
                "뮤지컬A", "설명", 120L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null, 1L, 1L, null);

        performance.updatePerformance("뮤지컬B", "수정설명", 130L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, 2L);

        assertThat(performance.getTitle()).isEqualTo("뮤지컬B");
        assertThat(performance.getHallId()).isEqualTo(2L);
    }

    @Test
    void 수정시_종료일이_시작일보다_빠르면_예외() {
        Performance performance = Performance.createPerformance(
                "뮤지컬A", "설명", 120L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null, 1L, 1L, null);

        assertThatThrownBy(() -> performance.updatePerformance("뮤지컬B", "설명", 120L,
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), null, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PerformanceErrorCode.INVALID_PERIOD.getMessage());
    }
}