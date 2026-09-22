package com.programmers.kdt.config;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import com.programmers.kdt.common.jwt.JwtProvider;
import com.programmers.kdt.filter.JwtAuthFilterFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * properties 기반 spring.cloud.gateway.server.webmvc.routes 대신 Java RouterFunction으로 라우트를 정의한다.
 * JwtAuthFilterFunction(커스텀 HandlerFilterFunction)을 붙이려면 코드로 라우트를 만들어야 하기 때문.
 */
@Configuration
public class GatewayRouteConfig {

    @Value("${user-service.url}")
    private String userServiceUrl;

    @Value("${order-service.url}")
    private String orderServiceUrl;

    @Value("${payment-service.url}")
    private String paymentServiceUrl;

    @Value("${performance-service.url}")
    private String performanceServiceUrl;

    @Bean
    public RouterFunction<ServerResponse> gatewayRoutes(JwtProvider jwtProvider) {
        JwtAuthFilterFunction jwtAuthFilterFunction = new JwtAuthFilterFunction(jwtProvider);

        RequestPredicate orderServicePaths = path("/api/order/**");

        RequestPredicate paymentServicePaths = path("/api/payments/**")
                .or(path("/api/points/**"))
                .or(path("/api/settlements/**"));

        RequestPredicate performanceServicePaths = path("/api/performances/**")
                .or(path("/api/performance/**"))
                .or(path("/api/v2/performances/**"))
                .or(path("/api/venues/**"))
                .or(path("/api/halls/**"))
                .or(path("/api/tickets/**"))
                .or(path("/api/standby/**"))
                .or(path("/api/images/**"))
                ;

        return route("user-service")
                .route(path("/api/users/**"), http())
                .before(uri(userServiceUrl))
                .filter(jwtAuthFilterFunction)
                .build()
            .and(route("order-service")
                .route(orderServicePaths, http())
                .before(uri(orderServiceUrl))
                .filter(jwtAuthFilterFunction)
                .build())
            .and(route("payment-service")
                .route(paymentServicePaths, http())
                .before(uri(paymentServiceUrl))
                .filter(jwtAuthFilterFunction)
                .build())
            .and(route("performance-service")
                .route(performanceServicePaths, http())
                .before(uri(performanceServiceUrl))
                .filter(jwtAuthFilterFunction)
                .build());
    }
}
