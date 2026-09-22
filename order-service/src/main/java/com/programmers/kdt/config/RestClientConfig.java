package com.programmers.kdt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient orderRestClient(
            @Value("${order-service.url}") String orderServiceUrl,
            @Value("${internal.auth.token}") String internalToken
    ){
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        return RestClient.builder()
                .baseUrl(orderServiceUrl)
                .requestFactory(requestFactory)
                .defaultHeader(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken)
                .build();
    }

    // 이벤트 수신 측은 소비자 로직(예: 티켓 예약 호출)을 동기로 실행하므로 읽기 타임아웃을 넉넉히 둔다.
    // 응답이 늦어 발신 측이 실패로 보더라도 소비자가 멱등이라 재시도해도 안전하다.
    @Bean
    public RestClient orderEventRestClient(
            @Value("${order-service.url}") String orderServiceUrl,
            @Value("${internal.auth.token}") String internalToken
    ){
        return eventRestClient(orderServiceUrl, internalToken);
    }

    @Bean
    public RestClient paymentEventRestClient(
            @Value("${payment-service.url}") String paymentServiceUrl,
            @Value("${internal.auth.token}") String internalToken
    ){
        return eventRestClient(paymentServiceUrl, internalToken);
    }

    private RestClient eventRestClient(String baseUrl, String internalToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken)
                .build();
    }

    @Bean
    public RestClient performanceRestClient(
            @Value("${performance-service.url}") String performanceServiceUrl
    ){
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        return RestClient.builder()
                .baseUrl(performanceServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
