package com.programmers.kdt.performance.application.service;

import com.programmers.kdt.performance.presentation.dto.RegisterPerformanceRequest;
import com.programmers.kdt.performance.domain.entity.Performance;
import com.programmers.kdt.performance.domain.entity.PerformanceStatus;
import com.programmers.kdt.performance.domain.event.PerformanceCacheEvictEvent;
import com.programmers.kdt.performance.domain.event.PerformanceDocumentSaveEvent;
import com.programmers.kdt.performance.domain.event.PerformanceSeatPriceRegisterEvent;
import com.programmers.kdt.performance.domain.event.PerformanceSessionRegisterEvent;
import com.programmers.kdt.performance.infrastructure.repository.PerformanceRepository;
import com.programmers.kdt.ticket.domain.entity.TicketStatus;
import com.programmers.kdt.ticket.domain.event.TicketIssueEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PerformanceV2Service {

    private final PerformanceRepository performanceRepository;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public void registerPerformanceInformation(RegisterPerformanceRequest request, Long sellerId) {
        // 1. 공연등록
        Performance performance = performanceRepository.save(request.toPerformance(sellerId));

        // 2. 회차등록
        publisher.publishEvent(new PerformanceSessionRegisterEvent(performance, request.sessionRequests()));

        // 3. 좌석 등급별 금액 등록
        publisher.publishEvent(new PerformanceSeatPriceRegisterEvent(performance, request.seatPriceRequests()));

        // 4. 캐시삭제
        publisher.publishEvent(new PerformanceCacheEvictEvent("performanceList", "all"));

        // 5. Ticket
        publisher.publishEvent(new TicketIssueEvent(performance.getPerformanceId(), TicketStatus.AVAILABLE));

        // 6. 검색용 문서 저장 (커밋 성공 후에만 색인)
        publisher.publishEvent(new PerformanceDocumentSaveEvent(performance));
    }

    @Transactional
    public int closedPerformance() {
        return performanceRepository.updateExpiredPerformances(PerformanceStatus.CLOSED, LocalDate.now());
    }
}
