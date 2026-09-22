package com.programmers.kdt.config;

import com.programmers.kdt.common.config.InternalAuthFilter;
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

    // 주문의 이벤트 수신 엔드포인트로 보낼 때 쓴다. 수신 측이 소비자 로직(예: 티켓 예약 호출)을
    // 동기로 실행하므로 읽기 타임아웃을 넉넉히 둔다. 응답이 늦어 이쪽에서 실패로 보더라도
    // 소비자가 멱등이라 outbox 재시도로 다시 보내도 안전하다.
    @Bean
    public RestClient orderEventRestClient(
            @Value("${order-service.url}") String orderServiceUrl,
            @Value("${internal.auth.token}") String internalToken
    ){
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
                .baseUrl(orderServiceUrl)
                .requestFactory(requestFactory)
                .defaultHeader(InternalAuthFilter.INTERNAL_TOKEN_HEADER, internalToken)
                .build();
    }
}
