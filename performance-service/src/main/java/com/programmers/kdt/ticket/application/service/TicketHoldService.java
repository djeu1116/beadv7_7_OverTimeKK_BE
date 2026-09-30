package com.programmers.kdt.ticket.application.service;

import com.programmers.kdt.ticket.presentation.dto.CheckTicketHoldAvailableRequest;
import com.programmers.kdt.ticket.presentation.dto.CheckTicketHoldAvailableResponse;

public interface TicketHoldService {
    CheckTicketHoldAvailableResponse checkTicketHoldStatus(CheckTicketHoldAvailableRequest request, Long userId);
}