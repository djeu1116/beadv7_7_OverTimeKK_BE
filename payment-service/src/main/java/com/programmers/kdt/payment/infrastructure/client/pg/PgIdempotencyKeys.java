package com.programmers.kdt.payment.infrastructure.client.pg;

// PointEventIds와 같은 목적 - PG 요청 재시도/재전달 시 같은 키를 재사용해야 하는 지점들의 키 형식을 한곳에 모은다.
public final class PgIdempotencyKeys {

    private PgIdempotencyKeys() {
    }

    // 결제 하나당 취소는 한 경로(명시적 실패 전 취소 / 환불 / give-up 안전망 취소)만 일어나므로
    // paymentId만으로 스코프해도 안전하다.
    public static String cancelKey(Long paymentId) {
        return "CANCEL:PAYMENT:" + paymentId;
    }
}
