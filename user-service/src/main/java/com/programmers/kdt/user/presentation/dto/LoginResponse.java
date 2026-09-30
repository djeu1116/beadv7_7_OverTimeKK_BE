package com.programmers.kdt.user.presentation.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken
) {
}
