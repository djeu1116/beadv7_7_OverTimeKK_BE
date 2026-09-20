package com.programmers.kdt.payment.client.order.rest;

// 주문 내부 API 응답을 결제 쪽에서 읽기 위한 형태. 주문의 OrderSnapshot과 필드만 맞춘 별개 타입이다.
public record OrderSnapshotResponse(
        Long orderId,
        Long userId,
        Long totalAmount
) {
}
