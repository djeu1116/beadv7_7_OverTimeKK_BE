package com.programmers.kdt.standby.presentation.dto;

import java.util.List;

public record CreateStandbyResponse(
        Long standbyId,
        List<String> zones,
        String status
) {
}
