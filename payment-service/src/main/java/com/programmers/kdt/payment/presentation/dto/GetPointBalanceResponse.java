package com.programmers.kdt.payment.presentation.dto;

public record GetPointBalanceResponse(
        Long userId,
        Long balance
) {

    public static GetPointBalanceResponse of(Long userId, Long balance) {
        return new GetPointBalanceResponse(userId, balance);
    }
}
