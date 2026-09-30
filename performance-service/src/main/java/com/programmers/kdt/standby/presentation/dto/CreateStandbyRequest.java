package com.programmers.kdt.standby.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateStandbyRequest(
        @NotNull Long performanceId,
        @NotNull Long sessionNum,
        @NotEmpty(message = "지망 구역은 최소 1개 이상 입력해야 합니다.")
        List<@NotBlank(message = "지망 구역 값은 비어있을 수 없습니다.") String> zones
) {
}
