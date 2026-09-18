package com.programmers.kdt.payment.client.refund;

// 결제는 PAID로 확정됐는데 그 다음 단계(티켓 예약 등)가 outbox relay 재시도를 다 소진하도록
// 계속 실패했을 때, 시스템이 스스로 트리거하는 보상 요청. 고객이 요청한 RefundRequestEvent와 달리
// 환불 정책 요율 계산 없이 전액 취소된다.
public record CompensationRequestEvent(
        Long paymentId
) {
}
