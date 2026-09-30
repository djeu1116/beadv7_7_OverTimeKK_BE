package com.programmers.kdt.payment.infrastructure.client.pg;

public record PgReadyCommand(
        Long orderId,
        Long amount
) {
}
