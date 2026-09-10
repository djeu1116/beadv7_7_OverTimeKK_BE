package com.programmers.kdt.performance.service.impl;

import com.programmers.kdt.common.PageConstants;
import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.exception.CommonErrorCode;
import com.programmers.kdt.image.service.S3ImageService;
import com.programmers.kdt.performance.dto.EndedTicketResponse;
import com.programmers.kdt.performance.dto.EndedTicketsResponse;
import com.programmers.kdt.performance.dto.FindPerformanceDto;
import com.programmers.kdt.performance.dto.FindPerformancesResponse;
import com.programmers.kdt.performance.dto.PerformanceSessionSeatDto;
import com.programmers.kdt.performance.dto.PerformanceSessionSeatResponse;
import com.programmers.kdt.performance.dto.SellerPerformanceResponse;
import com.programmers.kdt.performance.entity.Performance;
import com.programmers.kdt.performance.entity.PerformanceStatus;
import com.programmers.kdt.performance.exception.PerformanceErrorCode;
import com.programmers.kdt.performance.repository.PerformanceRepository;
import com.programmers.kdt.performance.service.FindPerformanceService;
import com.programmers.kdt.search.PerformanceDocument;
import com.programmers.kdt.search.PerformanceSearchRepository;
import com.programmers.kdt.venue.entity.Hall;
import com.programmers.kdt.venue.exception.VenueErrorCode;
import com.programmers.kdt.venue.repository.HallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FindPerformanceServiceImpl implements FindPerformanceService {

    private final PerformanceRepository performanceRepository;
    // no-es 프로필에서는 Elasticsearch 자동설정이 꺼져서 빈 자체가 없음 - Optional로 받아서
    // 그 외 메서드(목록/좌석/판매자 조회 등)는 ES 없이도 정상 동작하게 함
    private final Optional<PerformanceSearchRepository> performanceSearchRepository;
    private final HallRepository hallRepository;
    private final S3ImageService imageService;

    @Cacheable(value = "performanceList", key = "#page")
    @Override
    public FindPerformancesResponse findPerformances(PerformanceStatus status, int page) {
        if (page <= 0) {
            throw new BusinessException(CommonErrorCode.PAGE_BAD_REQUEST);
        }

        Pageable pageable = PageRequest.of(page-1, PageConstants.DEFAULT_PAGE_SIZE, Sort.by("endDate", "performanceId").descending());
        Page<Performance> performances = findPerformances(status, pageable);

        if (performances.isEmpty()) {
            throw new BusinessException(PerformanceErrorCode.FIND_PERFORMANCES_NO_RESULT);
        }

        long pageCount = (long) Math.ceil((double) performances.getTotalElements() / PageConstants.DEFAULT_PAGE_SIZE);
        return new FindPerformancesResponse(pageCount, convertToResponse(performances));
    }

    @Override
    @Transactional(readOnly = true)
    public EndedTicketsResponse findEndedPerformanceTickets(LocalDate from, LocalDate to) {
        log.info("요청기간 : {} ~ {}", from, to);
        List<EndedTicketResponse> endedTickets = performanceRepository.findEndedTickets(from, to);
        return new EndedTicketsResponse(from, to, endedTickets);
    }

    @Override
    public String getPerformanceTitle(Long performanceId) {
        Performance performance = performanceRepository.findById(performanceId).orElseThrow(
                () -> new BusinessException(PerformanceErrorCode.PERFORMANCE_NOT_FOUND)
        );
        return performance.getTitle();
    }

    @Override
    @Transactional(readOnly = true)
    public PerformanceSessionSeatResponse findPerformanceSessionSeats(Long performanceId) {
        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new BusinessException(PerformanceErrorCode.PERFORMANCE_NOT_FOUND));
        List<PerformanceSessionSeatDto> sessions = performanceRepository.findSessionSeatAvailability(performanceId);
        return new PerformanceSessionSeatResponse(performance.getPerformanceId(), performance.getTicketOpenAt(), sessions);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SellerPerformanceResponse> findSellerPerformances(Long sellerId) {
        return performanceRepository.findSellerPerformances(sellerId);
    }

    @Override
    @Transactional(readOnly = true)
    public FindPerformancesResponse searchPerformancesByTitle(String title) {
        PerformanceSearchRepository searchRepository = performanceSearchRepository
                .orElseThrow(() -> new BusinessException(PerformanceErrorCode.SEARCH_FEATURE_UNAVAILABLE));
        List<PerformanceDocument> documents = searchRepository.findTop8ByTitle(title);
        if (documents.isEmpty()) {
            throw new BusinessException(PerformanceErrorCode.FIND_PERFORMANCES_NO_RESULT);
        }

        List<Long> performanceIds = documents.stream()
                .map(PerformanceDocument::getPerformanceId)
                .toList();

        Map<Long, Performance> performancesById = new LinkedHashMap<>();
        performanceRepository.findAllById(performanceIds).forEach(performance ->
                performancesById.put(performance.getPerformanceId(), performance));

        List<Performance> performances = performanceIds.stream()
                .map(performancesById::get)
                .filter(Objects::nonNull)
                .toList();

        return new FindPerformancesResponse(1L, convertToResponse(performances));
    }

    private Page<Performance> findPerformances(PerformanceStatus status, Pageable pageable) {
        if (status == null ) {
            return performanceRepository.findAll(pageable);
        }
        return performanceRepository.findByPerformanceStatus(status, pageable);
    }

    private @NonNull List<FindPerformanceDto> convertToResponse(Iterable<Performance> performances) {
        List<FindPerformanceDto> responses = new ArrayList<>();
        for (Performance performance : performances) {
            Hall hall = hallRepository.findById(performance.getHallId())
                    .orElseThrow(() -> new BusinessException(VenueErrorCode.HALL_INFORMATION_NOT_FOUND));

            FindPerformanceDto response = new FindPerformanceDto(
                    performance.getPerformanceId(),
                    performance.getTitle(),
                    performance.getStartDate(),
                    performance.getEndDate(),
                    hall.getHallName(),
                    imageService.getPresignedGetUrl(performance.getImgPath())
            );
            responses.add(response);
        }
        return responses;
    }
}
