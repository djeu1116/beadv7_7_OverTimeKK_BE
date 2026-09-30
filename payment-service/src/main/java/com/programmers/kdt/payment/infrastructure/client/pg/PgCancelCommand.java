package com.programmers.kdt.payment.infrastructure.client.pg;

// idempotencyKey: 네트워크 재시도나 상위 계층(outbox relay 등)의 이벤트 재전달로 같은 취소가
// 두 번 나가도 Toss가 두 번째는 캐시된 응답으로 멱등 처리하게 한다(M-2). 결제 하나의 생애주기에서
// 취소는 한 경로(명시적 실패 전 취소 / 환불 / give-up 안전망 취소)만 일어나므로 paymentId 기반 키로 충분하다.
public record PgCancelCommand(
        String transactionKey,
        Long cancelAmount,
        String reason,
        String idempotencyKey
) {
}
