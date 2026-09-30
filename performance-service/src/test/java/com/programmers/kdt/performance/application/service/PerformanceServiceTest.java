package com.programmers.kdt.performance.application.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.image.application.service.S3ImageService;
import com.programmers.kdt.performance.presentation.dto.PerformanceDetailResponse;
import com.programmers.kdt.performance.presentation.dto.PerformanceRequest;
import com.programmers.kdt.performance.presentation.dto.PerformanceResponse;
import com.programmers.kdt.performance.domain.entity.Performance;
import com.programmers.kdt.performance.domain.exception.PerformanceErrorCode;
import com.programmers.kdt.performance.infrastructure.repository.PerformanceRepository;
import com.programmers.kdt.performance.infrastructure.repository.PerformanceSeatPriceRepository;
import com.programmers.kdt.performance.infrastructure.repository.PerformanceSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PerformanceServiceTest {

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private PerformanceSessionRepository performanceSessionRepository;

    @Mock
    private PerformanceSeatPriceRepository performanceSeatPriceRepository;

    @Mock
    private S3ImageService imageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PerformanceService performanceService;

    @Test
    void 공연_등록_성공() {
        // given
        PerformanceRequest request = new PerformanceRequest(
                "뮤지컬A", "설명", 120L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                null, 1L, null);
        Long sellerId = 1L;

        Performance saved = Performance.createPerformance(
                "뮤지컬A", "설명", 120L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                null, sellerId, 1L, null);
        ReflectionTestUtils.setField(saved, "performanceId", 1L);   // @Id는 setter가 없어 테스트에서 주입
        given(performanceRepository.save(any(Performance.class))).willReturn(saved);

        // when
        PerformanceResponse res = performanceService.registerPerformance(request, sellerId);

        // then
        assertThat(res.performanceId()).isEqualTo(1L);
        assertThat(res.title()).isEqualTo("뮤지컬A");
        verify(performanceRepository).save(any(Performance.class));
    }

    @Test
    void 공연_수정_성공() {
        Performance performance = Performance.createPerformance(
                "뮤지컬A", "설명", 120L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null, 1L, 1L, null);
        given(performanceRepository.findById(1L)).willReturn(Optional.of(performance));

        PerformanceRequest request = new PerformanceRequest(
                "뮤지컬B", "수정설명", 130L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, 2L, null);

        performanceService.updatePerformance(1L, request, 1L);

        assertThat(performance.getTitle()).isEqualTo("뮤지컬B");
        assertThat(performance.getHallId()).isEqualTo(2L);
    }

    @Test
    void 없는_공연_수정시_예외() {
        given(performanceRepository.findById(999L)).willReturn(Optional.empty());
        PerformanceRequest request = new PerformanceRequest(
                "뮤지컬B", "설명", 130L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, 2L, null);

        assertThatThrownBy(() -> performanceService.updatePerformance(999L, request, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PerformanceErrorCode.PERFORMANCE_NOT_FOUND.getMessage());
    }

    @Test
    void 판매자_본인이_아니면_수정_예외() {
        Performance performance = Performance.createPerformance(
                "뮤지컬A", "설명", 120L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null, 1L, 1L, null);
        given(performanceRepository.findById(1L)).willReturn(Optional.of(performance));

        PerformanceRequest request = new PerformanceRequest(
                "뮤지컬B", "설명", 130L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, 2L, null);

        assertThatThrownBy(() -> performanceService.updatePerformance(1L, request, 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PerformanceErrorCode.NOT_PERFORMANCE_OWNER.getMessage());
    }

    @Test
    void 공연_삭제_성공() {
        Performance performance = Performance.createPerformance(
                "뮤지컬A", "설명", 120L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null, 1L, 1L, null);
        given(performanceRepository.findById(1L)).willReturn(Optional.of(performance));

        performanceService.deletePerformance(1L, 1L);

        verify(performanceSeatPriceRepository).deleteByPerformance_PerformanceId(1L);
        verify(performanceSessionRepository).deleteByPerformanceSessionId_PerformanceId(1L);
        verify(performanceRepository).delete(performance);
    }

    @Test
    void 없는_공연_삭제시_예외() {
        given(performanceRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.deletePerformance(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PerformanceErrorCode.PERFORMANCE_NOT_FOUND.getMessage());
        verify(performanceRepository, never()).delete(any(Performance.class));
    }

    @Test
    void 판매자_본인이_아니면_삭제_예외() {
        Performance performance = Performance.createPerformance(
                "뮤지컬A", "설명", 120L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null, 1L, 1L, null);
        given(performanceRepository.findById(1L)).willReturn(Optional.of(performance));

        assertThatThrownBy(() -> performanceService.deletePerformance(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PerformanceErrorCode.NOT_PERFORMANCE_OWNER.getMessage());
        verify(performanceRepository, never()).delete(any(Performance.class));
    }

    @Test
    void 공연_단건_조회_성공() {
        Performance performance = Performance.createPerformance(
                "뮤지컬A", "설명", 120L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null, 1L, 2L, null);
        given(performanceRepository.findById(1L)).willReturn(Optional.of(performance));

        PerformanceDetailResponse res = performanceService.getPerformanceDetail(1L);

        assertThat(res.title()).isEqualTo("뮤지컬A");
        assertThat(res.hallId()).isEqualTo(2L);
    }

    @Test
    void 없는_공연_단건_조회시_예외() {
        given(performanceRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.getPerformanceDetail(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PerformanceErrorCode.PERFORMANCE_NOT_FOUND.getMessage());
    }

    @Test
    void 공연_목록_조회_성공() {
        Performance a = Performance.createPerformance(
                "뮤지컬A", "설명", 120L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null, 1L, 1L, null);
        Performance b = Performance.createPerformance(
                "뮤지컬B", "설명", 130L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, 2L, 2L, null);
        given(performanceRepository.findAll()).willReturn(List.of(a, b));

        List<PerformanceDetailResponse> result = performanceService.getPerformances();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PerformanceDetailResponse::title)
                .containsExactly("뮤지컬A", "뮤지컬B");
    }
}