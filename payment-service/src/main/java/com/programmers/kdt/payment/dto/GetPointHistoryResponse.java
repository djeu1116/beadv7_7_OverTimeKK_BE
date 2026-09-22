package com.programmers.kdt.payment.dto;

import com.programmers.kdt.payment.entity.PointType;

import java.time.LocalDateTime;

public record GetPointHistoryResponse(
    Long id,
    LocalDateTime transactedAt,
    PointType type,
    Long amount,
    Long balanceAfter

) {
    public static GetPointHistoryResponse of(Long id, LocalDateTime transactedAt, PointType type, Long amount, Long balanceAfter) {
        return new GetPointHistoryResponse(id, transactedAt, type, amount, balanceAfter);
    }

}
