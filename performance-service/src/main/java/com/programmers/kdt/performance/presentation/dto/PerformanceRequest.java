package com.programmers.kdt.performance.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PerformanceRequest(
        @NotBlank String title,
        String description,             // nullable
        @NotNull Long runtime,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime ticketOpenAt,     // nullable
        @NotNull Long hallId,
        String postUrl
) {
}
