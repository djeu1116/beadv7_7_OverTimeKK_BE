package com.programmers.kdt.payment.client.point;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Profile("performance-api")
@Component
public class RestEndedPerformanceClient implements EndedPerformanceClient {

    private final RestClient restClient;

    public RestEndedPerformanceClient(@Value("${performance-service.url}") String performanceServiceUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(performanceServiceUrl)
                .requestFactory(requestFactory)
                .build();

    }

    @Override
    public List<EndedTicket> findEndedTickets(LocalDate from, LocalDate to) {
        try {
            ApiResponse<EndedTicketsResponse> response = restClient.get()
                    .uri("/api/performances/ended?from={from}&to={to}", from, to)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<EndedTicketsResponse>>() {
                    });
            if (response == null || response.data() == null) {
                return List.of();
            }
            return response.data().endedTickets();
        } catch (RestClientResponseException e) {
            throw new BusinessException(PaymentErrorCode.PERFORMANCE_SERVICE_REQUEST_FAILED);
        }
    }
}
