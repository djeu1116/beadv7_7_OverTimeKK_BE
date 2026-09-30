package com.programmers.kdt.user.presentation.dto;

public record QueueEnterResponse(
        String status,
        String token,
        Long position
) {
}
