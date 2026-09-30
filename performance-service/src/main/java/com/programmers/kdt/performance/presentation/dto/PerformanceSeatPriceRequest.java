package com.programmers.kdt.performance.presentation.dto;

import com.programmers.kdt.performance.domain.entity.Performance;
import com.programmers.kdt.performance.domain.entity.PerformanceSeatPrice;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PerformanceSeatPriceRequest(
        @NotBlank(message = "구역 정보는 필수 입력값입니다.")
        String zone,

        @NotNull(message = "구역의 금액을 설정해주세요.")
        Long price
) {
    public PerformanceSeatPrice toSeatPrice(Performance performance) {
        return PerformanceSeatPrice.createInitial(performance, zone, price);
    }
}
