package com.programmers.kdt.payment.application.service;

import com.programmers.kdt.common.contract.OrderCancelRequestedEvent;
import com.programmers.kdt.payment.infrastructure.client.refund.CompensationRequestEvent;
import com.programmers.kdt.payment.infrastructure.client.refund.RefundRequestEvent;
import com.programmers.kdt.payment.presentation.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    // 결제 생성
    CreatePaymentResponse pay(String idempotencyKey, CreatePaymentRequest request, Long userId);

    // 결제 승인
    ConfirmPaymentResponse confirm(Long paymentId, ConfirmPaymentRequest request, String idempotencyKey, Long userId);

    // 결제 실패
    FailPaymentResponse fail(Long paymentId, FailPaymentRequest request, Long userId);

    // 결제 내역 조회
    Page<GetPaymentHistoryResponse> getPaymentHistory(Long userId, Pageable pageable);

    // 주문 취소 접수 수신 - 환불 접수
    void onOrderCancelRequested(OrderCancelRequestedEvent event);

    void onRefundRequested(RefundRequestEvent event);

    // 결제는 확정됐는데 후속 단계가 영구 실패해서 시스템이 트리거하는 보상(전액 취소)
    void onRefundRequested(CompensationRequestEvent event);

    // 환불 내역 조회
    Page<GetPaymentRefundHistoryResponse> getRefundHistory (Long paymentId, Long userId, Pageable pageable);






}
