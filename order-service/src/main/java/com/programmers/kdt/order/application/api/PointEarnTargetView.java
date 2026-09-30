package com.programmers.kdt.order.application.api;

public record PointEarnTargetView(
        Long userId,
        Long ticketId,
        Long ticketPrice
) {
}
