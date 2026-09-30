package com.programmers.kdt.ticket.application.service;

import com.programmers.kdt.ticket.presentation.dto.ReservedTicketRequest;

public interface TicketReserveService {
    void reservedTicket(ReservedTicketRequest request);
}