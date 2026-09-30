package com.programmers.kdt.ticket.application.service.impl;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.standby.domain.event.StandbyCheckResponseEvent;
import com.programmers.kdt.ticket.infrastructure.cache.TicketZoneCacheStore;
import com.programmers.kdt.ticket.presentation.dto.CancelTicketStatusRequest;
import com.programmers.kdt.ticket.presentation.dto.ReleaseTicketHoldRequest;
import com.programmers.kdt.ticket.presentation.dto.SessionZoneKey;
import com.programmers.kdt.ticket.domain.entity.Ticket;
import com.programmers.kdt.ticket.domain.entity.TicketStatus;
import com.programmers.kdt.ticket.domain.event.StandbyCheckRequestEvent;
import com.programmers.kdt.ticket.domain.exception.TicketErrorCode;
import com.programmers.kdt.ticket.infrastructure.repository.TicketRepository;
import com.programmers.kdt.ticket.application.service.TicketReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketReleaseServiceImpl implements TicketReleaseService {

    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TicketZoneCacheStore ticketZoneCacheStore;

    @Override
    @Transactional
    public void releaseHoldTicket(ReleaseTicketHoldRequest request) {
        Ticket ticket = getTicket(request.ticketId());
        validatePossibleReleasedHoldTicket(ticket, request.holdKey());
        releaseTicket(ticket);
    }

    @Override
    @Transactional
    public void releaseExpiredHoldTicket(Long ticketId, Map<SessionZoneKey, Boolean> zoneAvailabilityCache) {
        Ticket ticket = getTicket(ticketId);
        if (ticket.getTicketStatus() != TicketStatus.HOLD) {
            return;
        }
        releaseTicket(ticket, zoneAvailabilityCache);
    }

    @Override
    @Transactional
    public void changeTicketStatusByStandby(StandbyCheckResponseEvent event) {
        if (!event.existsStandby()) {
            Ticket ticket = getTicket(event.ticketId());
            ticket.releaseToAvailable();
            ticketZoneCacheStore.markAvailable(ticket);
        }
    }

    @Override
    @Transactional
    public void cancelReservedTicket(CancelTicketStatusRequest request) {
        Ticket ticket = getTicket(request.ticketId());
        ticket.validateReservedStatus(request.userId());
        releaseTicket(ticket);
    }

    private static void validatePossibleReleasedHoldTicket(Ticket ticket, String holdKey) {
        ticket.validateHoldStatus();

        if (LocalDateTime.now().isBefore(ticket.getHoldExpiredAt())) {
            if (holdKey == null || !holdKey.equals(ticket.getHoldKey())) {
                throw new BusinessException(TicketErrorCode.HOLD_KEY_DISCREPANCY);
            }
        }
    }

    private void releaseTicket(Ticket ticket) {
        releaseTicket(ticket, new HashMap<>());
    }

    private void releaseTicket(Ticket ticket, Map<SessionZoneKey, Boolean> availabilityCache) {
        SessionZoneKey key = new SessionZoneKey(ticket.getPerformanceId(), ticket.getSessionNum(), ticket.getZone());
        boolean existsAvailableInZone = availabilityCache.computeIfAbsent(key, k ->
                ticketRepository.existsByPerformanceIdAndSessionNumAndTicketStatusAndZone(k.performanceId(), k.sessionNum(), TicketStatus.AVAILABLE, k.zone()));
        log.info("{} 공연 {}회차 {} 구역 매진이 아닌가? {}", ticket.getPerformanceId(), ticket.getSessionNum(), ticket.getZone(), existsAvailableInZone);

        if (existsAvailableInZone) {
            ticket.releaseToAvailable();
            ticketZoneCacheStore.markAvailable(ticket);
        } else {
            eventPublisher.publishEvent(new StandbyCheckRequestEvent(ticket.getPerformanceId(), ticket.getSessionNum(), ticket.getZone(), ticket.getTicketId()));
        }
    }

    private @NonNull Ticket getTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(TicketErrorCode.TICKET_NOT_FOUND));
    }
}