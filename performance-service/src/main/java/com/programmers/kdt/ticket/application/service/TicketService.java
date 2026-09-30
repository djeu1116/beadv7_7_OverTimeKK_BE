package com.programmers.kdt.ticket.application.service;

import com.programmers.kdt.standby.domain.event.StandbyTicketEvent;
import com.programmers.kdt.ticket.presentation.dto.CreateStandbyResponse;
import com.programmers.kdt.ticket.presentation.dto.OrderTicketResponse;
import com.programmers.kdt.ticket.presentation.dto.SessionStartDateResponse;
import com.programmers.kdt.ticket.presentation.dto.TicketZoneRequest;
import com.programmers.kdt.ticket.presentation.dto.TicketZonesResponse;

import java.util.List;

public interface TicketService {
    CreateStandbyResponse issueStandby(Long userId, Long sessionNum, String zone);

    SessionStartDateResponse getSessionStartDate(Long ticketId);

    void standbyTicket(StandbyTicketEvent event);

    List<OrderTicketResponse> findOrderedTicketInfo(List<Long> ticketIds);

    TicketZonesResponse getTicketZone(TicketZoneRequest request);

    List<Long> findExpiredHoldTicketIds();
}