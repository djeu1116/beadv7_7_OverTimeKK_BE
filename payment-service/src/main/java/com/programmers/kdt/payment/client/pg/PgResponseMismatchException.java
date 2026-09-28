package com.programmers.kdt.payment.client.pg;

// PG가 승인/성공(DONE)이라고 응답했는데 그 응답의 orderId/금액이 우리가 기대한 값과 다를 때.
// PgClientException(PG가 명시적으로 거절)과는 구분한다 - 이건 PG는 "성공"이라 답했는데 내용이 안 맞는,
// 훨씬 심각한 이상 상황(데이터 오염 또는 위변조 가능성)이라 곧바로 신뢰해서 SUCCESS/FAILED로 확정지으면 안 된다.
public class PgResponseMismatchException extends RuntimeException {

    public PgResponseMismatchException(String paymentKey, String expectedOrderId, Long expectedAmount,
                                        String actualOrderId, Long actualAmount) {
        super("PG 응답 불일치 - paymentKey=%s, expected(orderId=%s, amount=%d), actual(orderId=%s, amount=%d)"
                .formatted(paymentKey, expectedOrderId, expectedAmount, actualOrderId, actualAmount));
    }
}
