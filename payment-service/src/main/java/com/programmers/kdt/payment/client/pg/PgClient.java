package com.programmers.kdt.payment.client.pg;

public interface PgClient {
    PgReadyResult ready(PgReadyCommand command);
    PgApproveResult approve(PgApproveCommand command);
    PgCancelResult cancel(PgCancelCommand command);

    // 성공(success=true) 응답이면 그 안의 orderId/금액이 expectedPgOrderId/expectedAmount와
    // 일치하는지까지 확인한다 - 다르면 PgResponseMismatchException. 재조회 결과를 그대로 신뢰하지 않기 위함(M-1).
    PgApproveResult select(String paymentKey, String expectedPgOrderId, Long expectedAmount);
}
