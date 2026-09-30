package com.programmers.kdt.order.infrastructure.client;

import com.programmers.kdt.common.contract.OrderCancelRequestedEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestOrderEventPublisherTest {

    private HttpServer server;
    private RestOrderEventPublisher publisher;
    private int status = 200;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> token = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .defaultHeader("X-Internal-Token", "test-token")
                .build();
        publisher = new RestOrderEventPublisher(restClient);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("주문 취소 접수 이벤트를 결제 내부 엔드포인트로 JSON POST하고 내부 토큰을 싣는다.")
    void postsCancelRequested() {
        publisher.publishCancelRequested(new OrderCancelRequestedEvent(1L, "단순 변심"));

        assertThat(path.get()).isEqualTo("/internal/events/order-cancel-requested");
        assertThat(body.get()).contains("\"orderId\":1").contains("단순 변심");
        assertThat(token.get()).isEqualTo("test-token");
    }

    @Test
    @DisplayName("수신 측이 오류를 응답하면 예외를 던진다 - outbox 릴레이가 재시도하도록.")
    void throwsWhenReceiverFails() {
        status = 500;

        assertThatThrownBy(() -> publisher.publishCancelRequested(new OrderCancelRequestedEvent(1L, "단순 변심")))
                .isInstanceOf(RestClientException.class);
    }
}
