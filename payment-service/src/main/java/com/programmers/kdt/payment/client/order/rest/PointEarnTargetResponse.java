package com.programmers.kdt.payment.client.order.rest;

public record PointEarnTargetResponse(
        Long userId,
        Long ticketId,
        Long ticketPrice
) {
}
