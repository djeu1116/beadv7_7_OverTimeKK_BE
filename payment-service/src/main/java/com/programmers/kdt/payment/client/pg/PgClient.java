package com.programmers.kdt.payment.client.pg;

public interface PgClient {
    PgReadyResult ready(PgReadyCommand command);
    PgApproveResult approve(PgApproveCommand command);
    PgCancelResult cancel(PgCancelCommand command);
    PgApproveResult select(String paymentKey);
}
