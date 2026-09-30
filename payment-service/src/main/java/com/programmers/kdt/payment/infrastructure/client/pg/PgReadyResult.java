package com.programmers.kdt.payment.infrastructure.client.pg;

public record PgReadyResult(
        String transactionKey,
        String orderId,
        String redirectionUrl
) {
}
