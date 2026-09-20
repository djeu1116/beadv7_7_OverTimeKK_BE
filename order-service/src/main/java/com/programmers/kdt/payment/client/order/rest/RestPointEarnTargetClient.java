package com.programmers.kdt.payment.client.order.rest;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.payment.client.order.PointEarnTargetClient;
import com.programmers.kdt.payment.dto.PointEarnTarget;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Profile("order-api")
@Component
@RequiredArgsConstructor
public class RestPointEarnTargetClient implements PointEarnTargetClient {

    private final RestClient orderRestClient;

    @Override
    public List<PointEarnTarget> findEarnTargets(List<Long> ticketIds) {
        try {
            ApiResponse<List<PointEarnTargetResponse>> response = orderRestClient.post()
                    .uri("/internal/orders/point-earn-targets")
                    .body(ticketIds)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            return response.data().stream()
                    .map(target -> new PointEarnTarget(target.userId(), target.ticketId(), target.ticketPrice()))
                    .toList();
        } catch (RestClientException e) {
            log.error("주문 서비스 호출 실패 - action=포인트 적립 대상 조회, ticketCount={}", ticketIds.size(), e);
            throw new BusinessException(PaymentErrorCode.ORDER_SERVICE_FAILED);
        }
    }
}
