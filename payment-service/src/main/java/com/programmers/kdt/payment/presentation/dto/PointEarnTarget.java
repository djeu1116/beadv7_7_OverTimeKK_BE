package com.programmers.kdt.payment.presentation.dto;

public record PointEarnTarget(
        Long userId,
        Long ticketId,
        Long ticketPrice
) {
}
