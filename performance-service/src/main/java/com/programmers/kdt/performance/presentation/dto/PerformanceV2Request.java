package com.programmers.kdt.performance.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.programmers.kdt.performance.domain.entity.Performance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PerformanceV2Request(
        @NotBlank (message = "공연 타이틀을 입력해주세요.")
        String title,

        String description,             // nullable

        @NotNull (message = "러닝 타임(분)을 입력해주세요.")
        Long runtime,

        @NotNull (message = "공연 시작일자를 입력해주세요.")
        LocalDate startDate,

        @NotNull (message = "공연 종료일자를 입력해주세요.")
        LocalDate endDate,


        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime ticketOpenAt,     // nullable

        @NotNull (message = "공연장을 입력해주세요.")
        Long hallId,

        String postUrl
) {
        public Performance toPerformance(Long userId) {
                return Performance.createPerformance(
                        title,
                        description,
                        runtime,
                        startDate,
                        endDate,
                        ticketOpenAt,
                        userId,
                        hallId,
                        postUrl
                );
        }
}
