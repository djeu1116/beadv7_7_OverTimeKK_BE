package com.programmers.kdt.payment.infrastructure.client.pg;

import java.time.LocalDateTime;

public record PgApproveResult(
        boolean success,
        LocalDateTime approvedAt
) {
}
