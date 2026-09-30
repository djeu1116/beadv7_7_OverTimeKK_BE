package com.programmers.kdt.performance.application.service.impl;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.performance.presentation.dto.PerformanceSessionRequest;
import com.programmers.kdt.performance.presentation.dto.PerformanceSessionResponse;
import com.programmers.kdt.performance.domain.entity.Performance;
import com.programmers.kdt.performance.domain.entity.PerformanceSession;
import com.programmers.kdt.performance.domain.entity.PerformanceSessionId;
import com.programmers.kdt.performance.domain.exception.PerformanceErrorCode;
import com.programmers.kdt.performance.infrastructure.repository.PerformanceRepository;
import com.programmers.kdt.performance.infrastructure.repository.PerformanceSessionRepository;
import com.programmers.kdt.performance.application.service.PerformanceSessionService;
import com.programmers.kdt.ticket.domain.entity.TicketStatus;
import com.programmers.kdt.ticket.infrastructure.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PerformanceSessionServiceImpl implements PerformanceSessionService {

    private final PerformanceSessionRepository sessionRepository;
    private final PerformanceRepository performanceRepository;
    private final TicketRepository ticketRepository;

    @Transactional
    public PerformanceSessionResponse registerPerformanceSession(PerformanceSessionRequest request, Long sellerId) {
        Performance performance = getPerformance(request.performanceId());
        validateOwner(performance, sellerId);
        PerformanceSession session = sessionRepository.save(request.toEntity(performance));

        ticketRepository.issueAdditionalTickets(request.performanceId(), request.sessionNum(), TicketStatus.AVAILABLE);

        return PerformanceSessionResponse.from(session);
    }

    @Transactional
    public PerformanceSessionResponse changePerformanceSession(PerformanceSessionRequest request, Long sellerId) {
        PerformanceSession session = getPerformanceSession(new PerformanceSessionId(request.sessionNum(), request.performanceId()));
        validateOwner(session.getPerformance(), sellerId);
        session.changePerformanceSession(request.actor(), request.performanceStartAt());
        return PerformanceSessionResponse.from(session);
    }

    @Transactional
    public void deletePerformanceSession(Long sessionNum, Long performanceId, Long sellerId) {
        PerformanceSessionId sessionId = new PerformanceSessionId(sessionNum, performanceId);
        PerformanceSession session = getPerformanceSession(sessionId);
        Performance performance = getPerformance(performanceId);
        validateOwner(performance, sellerId);
        validDeleteSessionTime(performance.getTicketOpenAt(), session.getPerformanceStartAt());
        sessionRepository.deleteById(sessionId);

        // 티켓 삭제
        // TODO: delCount = 0이면 Error 고민 필요
        ticketRepository.deleteByPerformanceIdAndSessionNum(performanceId, sessionNum);
    }

    @Transactional
    public void deletePerformanceSessions(Long performanceId, Long sellerId) {
        Performance performance = getPerformance(performanceId);
        validateOwner(performance, sellerId);
        validateDeletableBeforeTicketOpen(performance.getTicketOpenAt());
        sessionRepository.deleteByPerformanceSessionId_PerformanceId(performanceId);
    }

    private void validateOwner(Performance performance, Long sellerId) {
        if (!performance.getSellerId().equals(sellerId)) {
            throw new BusinessException(PerformanceErrorCode.NOT_PERFORMANCE_OWNER);
        }
    }

    public List<PerformanceSessionResponse> findAllPerformanceSessionsByPerformanceId(Long performanceId) {
        List<PerformanceSession> sessions = sessionRepository.findByPerformanceSessionId_PerformanceId(performanceId);
        if (sessions.isEmpty()) {
            throw new BusinessException(PerformanceErrorCode.PERFORMANCE_SESSION_NOT_FOUND);
        }

        List<PerformanceSessionResponse> responses = new ArrayList<>(sessions.size());
        for (PerformanceSession session : sessions) {
            responses.add(PerformanceSessionResponse.from(session));
        }

        return responses;
    }

    public PerformanceSessionResponse getPerformanceSession(Long sessionNum, Long performanceId) {
        PerformanceSession session = getPerformanceSession(new PerformanceSessionId(sessionNum, performanceId));
        return PerformanceSessionResponse.from(session);
    }


    private PerformanceSession getPerformanceSession(PerformanceSessionId sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(PerformanceErrorCode.PERFORMANCE_SESSION_NOT_FOUND));
    }

    private Performance getPerformance(Long performanceId) {
        return performanceRepository.findById(performanceId).orElseThrow(
                () -> new BusinessException(PerformanceErrorCode.PERFORMANCE_NOT_FOUND)
        );
    }

    private void validDeleteSessionTime(LocalDateTime ticketOpenAt, LocalDateTime performanceStartAt) {
        validateDeletableBeforeTicketOpen(ticketOpenAt);
        validateDeletableBeforePerformanceStart(performanceStartAt);

    }

    private static void validateDeletableBeforePerformanceStart(LocalDateTime performanceStartAt) {
        if (performanceStartAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(PerformanceErrorCode.PERFORMANCE_SESSION_UPDATE_NOT_ALLOWED_AFTER_PERFORMANCE_START, "삭제");
        }
    }

    private static void validateDeletableBeforeTicketOpen(LocalDateTime ticketOpenAt) {
        if (ticketOpenAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(PerformanceErrorCode.PERFORMANCE_SESSION_UPDATE_NOT_ALLOWED_AFTER_TICKET_OPEN, "삭제");
        }
    }
}
