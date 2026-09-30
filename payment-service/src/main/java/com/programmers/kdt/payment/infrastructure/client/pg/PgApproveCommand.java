package com.programmers.kdt.payment.infrastructure.client.pg;

public record PgApproveCommand(
        String transactionKey,
        String orderId,
        Long amount
) {
}
