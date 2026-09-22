package com.programmers.kdt.payment.client.refund;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.LocalDate;

@Profile("performance-api")
@Component
public class RestPerformanceClient implements PerformanceClient {

    private final RestClient restClient;

    public RestPerformanceClient(@Value("${performance-service.url}") String performanceServiceUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = RestClient.builder()
                .baseUrl(performanceServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public LocalDate getPerformanceDate(Long ticketId) {
        try {
            ApiResponse<TicketRefundDateResponse> response = restClient.get()
                    .uri("/api/tickets/{ticketId}/refund-date", ticketId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<TicketRefundDateResponse>>() {
                    });
            if (response == null || response.data() == null) {
                throw new BusinessException(PaymentErrorCode.PERFORMANCE_DATE_NOT_FOUND, ticketId);
            }

            return response.data().performanceDate();
        } catch (RestClientException e) {
            if (e instanceof RestClientResponseException rcre
                    && rcre.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new BusinessException(PaymentErrorCode.PERFORMANCE_DATE_NOT_FOUND, ticketId);
            }
            throw new BusinessException(PaymentErrorCode.PERFORMANCE_SERVICE_REQUEST_FAILED);
        }
    }
}
