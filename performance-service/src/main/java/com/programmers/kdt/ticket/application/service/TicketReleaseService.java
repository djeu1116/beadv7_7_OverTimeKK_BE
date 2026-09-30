package com.programmers.kdt.ticket.application.service;

import com.programmers.kdt.standby.domain.event.StandbyCheckResponseEvent;
import com.programmers.kdt.ticket.presentation.dto.CancelTicketStatusRequest;
import com.programmers.kdt.ticket.presentation.dto.ReleaseTicketHoldRequest;
import com.programmers.kdt.ticket.presentation.dto.SessionZoneKey;

import java.util.Map;

public interface TicketReleaseService {
    void releaseHoldTicket(ReleaseTicketHoldRequest request);

    void releaseExpiredHoldTicket(Long ticketId, Map<SessionZoneKey, Boolean> zoneAvailabilityCache);

    void changeTicketStatusByStandby(StandbyCheckResponseEvent event);

    void cancelReservedTicket(CancelTicketStatusRequest request);
}