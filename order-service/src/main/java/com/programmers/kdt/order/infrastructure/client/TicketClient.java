package com.programmers.kdt.order.infrastructure.client;

import com.programmers.kdt.order.presentation.dto.OrderTicketRequest;
import com.programmers.kdt.order.presentation.dto.TicketCancelRequest;
import com.programmers.kdt.order.presentation.dto.TicketReleaseRequest;
import com.programmers.kdt.order.presentation.dto.TicketReserveRequest;
import com.programmers.kdt.order.presentation.dto.ValidateTicketRequest;

import java.util.List;

public interface TicketClient {

    void validateTicket(ValidateTicketRequest ticketRequest);
    void reserveTicket(TicketReserveRequest ticketReserveRequest);
    void releaseSeat(TicketReleaseRequest request);
    void cancelTicket(TicketCancelRequest request);
    List<TicketInfo> getTickets(OrderTicketRequest request);
}
