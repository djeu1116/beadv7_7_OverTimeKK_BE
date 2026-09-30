package com.programmers.kdt.order.presentation.controller.internal;

import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.order.application.api.OrderPaymentApi;
import com.programmers.kdt.order.application.api.OrderSnapshot;
import com.programmers.kdt.order.application.api.PointEarnTargetView;
import com.programmers.kdt.order.application.api.StartPaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 결제 컨텍스트 전용 내부 API. 게이트웨이는 /api/** 만 라우팅하므로 /internal/** 는 외부에 노출되지 않는다.
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class OrderInternalController {

    private final OrderPaymentApi orderPaymentApi;

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderSnapshot>> findOrder(@PathVariable Long orderId) {
        return orderPaymentApi.findOrder(orderId)
                .map(snapshot -> ResponseEntity.ok(ApiResponse.success(snapshot)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 전이 실패도 예외가 아닌 결과값으로 돌려준다 - 호출 측이 자기 에러 체계로 해석한다.
    @PostMapping("/{orderId}/payment-start")
    public ApiResponse<StartPaymentResult> startPayment(@PathVariable Long orderId) {
        return ApiResponse.success(orderPaymentApi.startPayment(orderId));
    }

    @PostMapping("/{orderId}/payment-start/cancel")
    public ApiResponse<Void> cancelPaymentStart(@PathVariable Long orderId) {
        orderPaymentApi.cancelPaymentStart(orderId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{orderId}/ticket-id")
    public ApiResponse<Long> findTicketId(@PathVariable Long orderId) {
        return ApiResponse.success(orderPaymentApi.findTicketId(orderId));
    }

    @PostMapping("/point-earn-targets")
    public ApiResponse<List<PointEarnTargetView>> findPointEarnTargets(@RequestBody List<Long> ticketIds) {
        return ApiResponse.success(orderPaymentApi.findPointEarnTargets(ticketIds));
    }
}
