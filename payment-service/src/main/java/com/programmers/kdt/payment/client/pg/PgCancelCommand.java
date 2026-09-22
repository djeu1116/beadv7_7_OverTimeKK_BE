package com.programmers.kdt.payment.client.pg;

public record PgCancelCommand(
        String transactionKey,
        Long cancelAmount,
        String reason
) {
}
