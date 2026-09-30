package com.programmers.kdt.ticket.presentation.dto;

public record CreateStandbyResponse(
        Long standbyId,
        String zone,
        String status
) {
}