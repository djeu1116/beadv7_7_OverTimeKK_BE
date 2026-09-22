package com.programmers.kdt.payment.dto;

public record PointEarnTarget(
        Long userId,
        Long ticketId,
        Long ticketPrice
) {
}
