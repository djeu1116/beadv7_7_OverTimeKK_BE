package com.programmers.kdt.payment.client.pg;

public record PgReadyResult(
        String transactionKey,
        String orderId,
        String redirectionUrl
) {
}
