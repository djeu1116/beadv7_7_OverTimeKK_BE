package com.programmers.kdt.order.presentation.controller;

import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.order.presentation.dto.*;
import com.programmers.kdt.order.application.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // 주문 요청
    @PostMapping
    public ResponseEntity<ApiResponse<CreateOrderResponse>> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("X-User-Id") Long userId
    ) {
        CreateOrderResponse response = orderService.createOrder(request, userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    // 결제 전 주문 취소
    @PostMapping("/{orderId}/cancel-pending")
    public ApiResponse<CancelOrderResponse> cancelPendingOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        CancelOrderResponse response =
                orderService.cancelPendingOrder(orderId, userId);

        return ApiResponse.success(response);
    }

    // 결제 완료 후 주문 취소
    @PostMapping("/{orderId}/cancel-completed")
    public ApiResponse<CancelOrderResponse> cancelCompleteOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CancelOrderRequest request
    ) {
        CancelOrderResponse response = orderService.cancelCompletedOrder(orderId, userId, request);
        return ApiResponse.success(response);
    }

    // 주문 내역 조회
    @GetMapping
    public ApiResponse<List<GetOrderHistoryResponse>> getOrderHistory(@RequestHeader("X-User-Id") Long userId){
         List<GetOrderHistoryResponse> response = orderService.getOrderHistory(userId);
         return ApiResponse.success(response);
    }
}
