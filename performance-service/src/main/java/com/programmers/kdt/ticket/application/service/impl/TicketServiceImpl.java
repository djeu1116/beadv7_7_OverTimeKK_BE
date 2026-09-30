package com.programmers.kdt.ticket.application.service.impl;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.performance.infrastructure.cache.PerformanceSeatPriceCacheStore;
import com.programmers.kdt.performance.domain.entity.PerformanceSeatPrice;
import com.programmers.kdt.performance.domain.entity.PerformanceSession;
import com.programmers.kdt.performance.domain.entity.PerformanceSessionId;
import com.programmers.kdt.performance.domain.exception.PerformanceErrorCode;
import com.programmers.kdt.performance.infrastructure.repository.PerformanceSeatPriceRepository;
import com.programmers.kdt.performance.infrastructure.repository.PerformanceSessionRepository;
import com.programmers.kdt.standby.domain.event.StandbyTicketEvent;
import com.programmers.kdt.ticket.infrastructure.cache.TicketZoneCacheStore;
import com.programmers.kdt.ticket.presentation.dto.CreateStandbyResponse;
import com.programmers.kdt.ticket.presentation.dto.OrderTicketResponse;
import com.programmers.kdt.ticket.presentation.dto.SessionStartDateResponse;
import com.programmers.kdt.ticket.presentation.dto.SessionZoneKey;
import com.programmers.kdt.ticket.presentation.dto.TicketZoneRequest;
import com.programmers.kdt.ticket.presentation.dto.TicketZoneResponse;
import com.programmers.kdt.ticket.presentation.dto.TicketZonesResponse;
import com.programmers.kdt.ticket.domain.entity.Ticket;
import com.programmers.kdt.ticket.domain.exception.TicketErrorCode;
import com.programmers.kdt.ticket.infrastructure.repository.TicketRepository;
import com.programmers.kdt.ticket.application.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.programmers.kdt.ticket.domain.entity.TicketStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final PerformanceSessionRepository sessionRepository;
    private final PerformanceSeatPriceRepository performanceSeatPriceRepository;
    private final TicketZoneCacheStore ticketZoneCacheStore;
    private final PerformanceSeatPriceCacheStore performanceSeatPriceCacheStore;

    @Override
    public CreateStandbyResponse issueStandby(Long userId, Long sessionNum, String zone) {
        // TODO: (sessionNum, zone) 단위로 순번 채번
        return null;
    }

    @Override
    public SessionStartDateResponse getSessionStartDate(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(TicketErrorCode.TICKET_NOT_FOUND, ticketId));

        PerformanceSession session = sessionRepository.findById(new PerformanceSessionId(ticket.getSessionNum(), ticket.getPerformanceId()))
                .orElseThrow(() -> new BusinessException(PerformanceErrorCode.PERFORMANCE_SESSION_NOT_FOUND));

        LocalDate sessionStartDate = session.getPerformanceStartAt().toLocalDate();
        return new SessionStartDateResponse(sessionStartDate);
    }

    @Override
    @Transactional
    public void standbyTicket(StandbyTicketEvent event) {
        Ticket ticket = getTicket(event.ticketId());
        ticket.standbyTicket(event.standbyUserId(), event.standbyExpiredAt());
    }

    @Override
    public List<OrderTicketResponse> findOrderedTicketInfo(List<Long> ticketIds) {
        //  TODO : 페이징처리 필요
        if (ticketIds == null || ticketIds.isEmpty()) {
            return List.of();
        }
        return ticketRepository.findTicketInfoByTicketIds(ticketIds);
    }

    @Override
    public TicketZonesResponse getTicketZone(TicketZoneRequest request) {
        SessionZoneKey zoneKey = new SessionZoneKey(request.performanceId(), request.sessionNum(), request.zone());
        List<TicketZoneResponse> response = ticketZoneCacheStore.find(zoneKey)
                .orElseGet(() -> {
                    List<TicketZoneResponse> zones = ticketRepository.findByZoneAndPerformance(
                            request.performanceId(), request.sessionNum(), request.zone());
                    ticketZoneCacheStore.save(zoneKey, zones);
                    return zones;
                });
        Long price = performanceSeatPriceCacheStore.find(request.performanceId(), request.zone())
                .orElseGet(() -> {
                    PerformanceSeatPrice seatPrice = performanceSeatPriceRepository.findByPerformance_PerformanceIdAndZone(request.performanceId(), request.zone());
                    performanceSeatPriceCacheStore.save(request.performanceId(), request.zone(), seatPrice.getPrice());
                    return seatPrice.getPrice();
                });
        return new TicketZonesResponse(request.zone(), price, response);
    }

    @Override
    public List<Long> findExpiredHoldTicketIds() {
        return ticketRepository.findByTicketStatusAndHoldExpiredAtBefore(TicketStatus.HOLD, LocalDateTime.now())
                .stream()
                .map(Ticket::getTicketId)
                .toList();
    }

    private @NonNull Ticket getTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(TicketErrorCode.TICKET_NOT_FOUND));
    }
}