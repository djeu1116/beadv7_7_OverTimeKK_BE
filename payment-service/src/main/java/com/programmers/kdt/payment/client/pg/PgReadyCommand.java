package com.programmers.kdt.payment.client.pg;

public record PgReadyCommand(
        Long orderId,
        Long amount
) {
}
