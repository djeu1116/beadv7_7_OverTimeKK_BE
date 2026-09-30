package com.programmers.kdt.order.presentation.dto;

import java.time.LocalDateTime;

public record ValidateTicketRequest(
        Long ticketId,
        Long userId,
        Long price,
        String holdKey
) {
    public static ValidateTicketRequest from(CreateOrderRequest request, Long userId){
        return new ValidateTicketRequest(request.ticketId(), userId, request.price(), request.holdKey());
    }
}