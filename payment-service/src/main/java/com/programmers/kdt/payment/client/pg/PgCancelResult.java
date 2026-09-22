package com.programmers.kdt.payment.client.pg;

import java.time.LocalDateTime;

public record PgCancelResult(
        boolean success,
        LocalDateTime canceledAt
) {
}
