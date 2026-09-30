package com.programmers.kdt.user.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawRequest(
        @NotBlank
        String password
) {
}
