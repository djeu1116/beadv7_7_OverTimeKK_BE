package com.programmers.kdt.payment.client.order.rest;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.payment.client.order.OrderClient;
import com.programmers.kdt.payment.client.order.OrderInfo;
import com.programmers.kdt.payment.client.order.StartPaymentOutcome;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;

// 주문 컨텍스트를 HTTP로 호출하는 어댑터. 주문/결제가 한 프로세스에 있어도 경계 밖 호출로 다뤄서
// 서비스를 분리했을 때와 같은 실패 양상(타임아웃, 부분 실패)을 미리 드러낸다.
@Slf4j
@Profile("order-api")
@Component
@RequiredArgsConstructor
public class RestOrderClient implements OrderClient {

    private final RestClient orderRestClient;

    @Override
    public Optional<OrderInfo> findOrder(Long orderId) {
        try {
            ApiResponse<OrderSnapshotResponse> response = orderRestClient.get()
                    .uri("/internal/orders/{orderId}", orderId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            OrderSnapshotResponse snapshot = response.data();
            return Optional.of(new OrderInfo(snapshot.orderId(), snapshot.userId(), snapshot.totalAmount()));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw orderCallFailed("주문 조회", orderId, e);
        } catch (RestClientException e) {
            throw orderCallFailed("주문 조회", orderId, e);
        }
    }

    @Override
    public StartPaymentOutcome startPayment(Long orderId) {
        try {
            ApiResponse<StartPaymentOutcome> response = orderRestClient.post()
                    .uri("/internal/orders/{orderId}/payment-start", orderId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            return response.data();
        } catch (RestClientException e) {
            throw orderCallFailed("결제 시작 요청", orderId, e);
        }
    }

    @Override
    public void cancelPaymentStart(Long orderId) {
        try {
            orderRestClient.post()
                    .uri("/internal/orders/{orderId}/payment-start/cancel", orderId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw orderCallFailed("결제 시작 취소", orderId, e);
        }
    }

    @Override
    public Long getTicketId(Long orderId) {
        try {
            ApiResponse<Long> response = orderRestClient.get()
                    .uri("/internal/orders/{orderId}/ticket-id", orderId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            return response.data();
        } catch (RestClientException e) {
            throw orderCallFailed("티켓 조회", orderId, e);
        }
    }

    private BusinessException orderCallFailed(String action, Long orderId, Exception e) {
        log.error("주문 서비스 호출 실패 - action={}, orderId={}", action, orderId, e);
        return new BusinessException(PaymentErrorCode.ORDER_SERVICE_FAILED);
    }
}
