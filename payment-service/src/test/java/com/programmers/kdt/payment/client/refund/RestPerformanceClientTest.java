package com.programmers.kdt.payment.client.refund;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;


class RestPerformanceClientTest {

    private HttpServer server;
    private RestPerformanceClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        client = new RestPerformanceClient("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("정상 응답이면 performanceDate를 그대로 반환한다.")
    void getPerformanceDate_success() throws IOException {
        server.createContext("/api/tickets/1/refund-date", exchange -> {
            String body = "{\"success\":true,\"data\":{\"performanceDate\":\"2026-08-01\"},\"code\":null,\"message\":null}";
            respond(exchange, 200, body);
        });

        LocalDate result = client.getPerformanceDate(1L);

        assertThat(result).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    @DisplayName("404 응답이면 PERFORMANCE_DATE_NOT_FOUND 예외로 변환한다.")
    void getPerformanceDate_404() throws IOException {
        server.createContext("/api/tickets/999/refund-date", exchange -> {
            String body = "{\"success\":false,\"data\":null,\"code\":\"TKT404_001\",\"message\":\"존재하지 않는 티켓입니다.\"}";
            respond(exchange, 404, body);
        });

        assertThatThrownBy(() -> client.getPerformanceDate(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PERFORMANCE_DATE_NOT_FOUND);
    }

    @Test
    @DisplayName("5xx 응답이면 PERFORMANCE_SERVICE_REQUEST_FAILED 예외로 변환한다.")
    void getPerformanceDate_5xx() throws IOException {
        server.createContext("/api/tickets/1/refund-date", exchange -> respond(exchange, 500, ""));

        assertThatThrownBy(() -> client.getPerformanceDate(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PERFORMANCE_SERVICE_REQUEST_FAILED);
    }

    @Test
    @DisplayName("readTimeout(3s)보다 응답이 늦으면 ResourceAccessException이 그대로 전파된다.")
    void getPerformanceDate_timeout() {
        server.createContext("/api/tickets/1/refund-date", exchange -> {
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException ignored) {

            }
            respond(exchange, 200, "{}");
        });

        assertThatThrownBy(() -> client.getPerformanceDate(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PERFORMANCE_SERVICE_REQUEST_FAILED);

    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}