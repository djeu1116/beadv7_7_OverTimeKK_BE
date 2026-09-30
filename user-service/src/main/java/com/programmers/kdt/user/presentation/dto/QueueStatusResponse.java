package com.programmers.kdt.user.presentation.dto;

public record QueueStatusResponse(
        String status,
        Long position
) {
}
