package com.programmers.kdt.order.presentation.dto;

import com.programmers.kdt.order.infrastructure.client.TicketInfo;
import com.programmers.kdt.order.domain.entity.Order;

import java.time.LocalDateTime;

public record GetOrderHistoryResponse(
        Long orderId,
        String orderStatus,
        String performanceName,
        LocalDateTime orderedAt,
        String zone,
        int quantity,
        Long totalAmount
) {
    public static GetOrderHistoryResponse from(Order order, TicketInfo ticketInfo){
        return new GetOrderHistoryResponse(
                order.getOrderId(),
                order.getOrderStatus().name(),
                ticketInfo.performanceName(),
                order.getCreatedAt(),
                ticketInfo.zone(),
                order.getItems().size(),
                order.getTotalAmount()
        );
    }
}
