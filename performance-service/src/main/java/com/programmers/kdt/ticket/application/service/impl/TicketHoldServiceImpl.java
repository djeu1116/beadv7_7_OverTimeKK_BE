package com.programmers.kdt.ticket.application.service.impl;

import com.programmers.kdt.common.TimeLimits;
import com.programmers.kdt.common.constant.OrderTypeCode;
import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.exception.CommonErrorCode;
import com.programmers.kdt.common.util.TicketKeyGenerator;
import com.programmers.kdt.ticket.infrastructure.cache.TicketZoneCacheStore;
import com.programmers.kdt.ticket.presentation.dto.CheckTicketHoldAvailableRequest;
import com.programmers.kdt.ticket.presentation.dto.CheckTicketHoldAvailableResponse;
import com.programmers.kdt.ticket.domain.entity.Ticket;
import com.programmers.kdt.ticket.domain.exception.TicketErrorCode;
import com.programmers.kdt.ticket.infrastructure.repository.TicketRepository;
import com.programmers.kdt.ticket.application.service.TicketHoldService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TicketHoldServiceImpl implements TicketHoldService {

    private final TicketRepository ticketRepository;
    private final TicketZoneCacheStore ticketZoneCacheStore;

    @Override
    @Transactional
    public CheckTicketHoldAvailableResponse checkTicketHoldStatus(CheckTicketHoldAvailableRequest request, Long userId) {
        Ticket ticket = getTicketLock(request.ticketId());
        LocalDateTime holdExpiredAt = LocalDateTime.now().plusMinutes(TimeLimits.orderHoldTicket5Min);
        String holdKey = TicketKeyGenerator.generate();

        validateTicketConditionForHold(request.orderType(), ticket, userId);

        ticket.holdTicket(userId, holdExpiredAt, holdKey);
        ticketZoneCacheStore.markHold(ticket);
        return new CheckTicketHoldAvailableResponse(ticket.getTicketId(), ticket.getPrice(), holdExpiredAt, holdKey);
    }

    private void validateTicketConditionForHold(String orderType, Ticket ticket, Long userId) {
        switch (orderType) {
            case OrderTypeCode.GENERAL -> ticket.validateAvailableStatus();
            case OrderTypeCode.STANDBY -> ticket.validateStandbyStatus(userId);
            default -> throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "주문 구분 값");
        }
    }

    private @NonNull Ticket getTicketLock(Long ticketId) {
        return ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new BusinessException(TicketErrorCode.TICKET_NOT_FOUND));
    }
}