package com.programmers.kdt.payment.infrastructure.client.order.rest;

public record PointEarnTargetResponse(
        Long userId,
        Long ticketId,
        Long ticketPrice
) {
}
