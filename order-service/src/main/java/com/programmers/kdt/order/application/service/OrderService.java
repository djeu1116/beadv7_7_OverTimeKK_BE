package com.programmers.kdt.order.application.service;

import com.programmers.kdt.order.presentation.dto.*;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderService {

    // 주문 요청
    CreateOrderResponse createOrder(CreateOrderRequest request, Long userId);

    // 주문 완료
    void completeOrder(Long orderId);

    // 주문 만료
    void expireOrder(Long orderId, LocalDateTime now);

    // 주문 취소
    CancelOrderResponse cancelCompletedOrder(Long orderId, Long userId, CancelOrderRequest request);

    // 주문 내역 조회
    List<GetOrderHistoryResponse> getOrderHistory(Long userId);

    CancelOrderResponse cancelPendingOrder(Long orderId, Long userId);

    // 환불 성공 시 주문 취소 확정
    void confirmCancellation(Long orderId);

    // 환불 실패 시 주문 취소 접수 복구
    void revertCancellation(Long orderId);

    // 결제 후 후속 단계 실패로 보상(전액 환불)이 끝났을 때 주문 종료
    void failOrderAfterCompensation(Long orderId);

    // 결제 실패 시 주문 상태 변경
    void handlePaymentFailed(Long orderId);
}
