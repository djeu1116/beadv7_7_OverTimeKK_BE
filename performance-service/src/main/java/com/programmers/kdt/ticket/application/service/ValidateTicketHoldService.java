package com.programmers.kdt.ticket.application.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.ticket.presentation.dto.ValidateTicketHoldRequest;
import com.programmers.kdt.ticket.domain.entity.Ticket;
import com.programmers.kdt.ticket.domain.exception.TicketErrorCode;
import com.programmers.kdt.ticket.infrastructure.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ValidateTicketHoldService {

    private final TicketRepository ticketRepository;

    public void validateTicketHold(ValidateTicketHoldRequest request) {
        Ticket ticket = getTicket(request.ticketId());

        switch (ticket.getTicketStatus()) {
            case AVAILABLE -> throw new BusinessException(TicketErrorCode.TICKET_IS_NOT_HELD);
            case RESERVED -> throw new BusinessException(TicketErrorCode.ALREADY_RESERVED_TICKET);
        }

        if (ticket.getHoldExpiredAt() == null || LocalDateTime.now().isAfter(ticket.getHoldExpiredAt())) {
            throw new BusinessException(TicketErrorCode.HOLD_TICKET_EXPIRED);
        }

        if (!request.holdKey().equals(ticket.getHoldKey())) {
            throw new BusinessException(TicketErrorCode.HOLD_KEY_DISCREPANCY);
        }

        if (!request.price().equals(ticket.getPrice())) {
            throw new BusinessException(TicketErrorCode.TICKET_PRICE_DISCREPANCY);
        }

    }

    private @NonNull Ticket getTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(TicketErrorCode.TICKET_NOT_FOUND));
    }
}