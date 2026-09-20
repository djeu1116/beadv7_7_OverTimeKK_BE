package com.programmers.kdt.order.api;

public record PointEarnTargetView(
        Long userId,
        Long ticketId,
        Long ticketPrice
) {
}
