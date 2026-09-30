package com.programmers.kdt.order.infrastructure.client;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.order.presentation.dto.OrderTicketRequest;
import com.programmers.kdt.order.presentation.dto.TicketCancelRequest;
import com.programmers.kdt.order.presentation.dto.TicketReleaseRequest;
import com.programmers.kdt.order.presentation.dto.TicketReserveRequest;
import com.programmers.kdt.order.presentation.dto.ValidateTicketRequest;
import com.programmers.kdt.order.domain.exception.OrderErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketApiClientTest {

    private HttpServer server;
    private TicketApiClient ticketApiClient;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        RestClient restClient = RestClient.create(
                "http://localhost:" + server.getAddress().getPort()
        );
        ticketApiClient = new TicketApiClient(restClient);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Nested
    @DisplayName("티켓 점유 검증")
    class ValidateTicket {

        @Test
        @DisplayName("검증 API에 요청 정보를 POST 방식으로 전달한다")
        void success() {
            AtomicReference<String> method = new AtomicReference<>();
            AtomicReference<String> body = new AtomicReference<>();
            server.createContext("/api/tickets/hold/validation", exchange -> {
                method.set(exchange.getRequestMethod());
                body.set(readBody(exchange));
                respond(exchange, 200, "");
            });

            ticketApiClient.validateTicket(
                    new ValidateTicketRequest(10L, 20L, 50_000L, "hold-key")
            );

            assertThat(method.get()).isEqualTo("POST");
            assertThat(body.get())
                    .contains("\"ticketId\":10")
                    .contains("\"userId\":20")
                    .contains("\"price\":50000")
                    .contains("\"holdKey\":\"hold-key\"");
        }

        @Test
        @DisplayName("404 응답은 TICKET_NOT_FOUND 예외로 변환한다")
        void notFound() {
            registerErrorResponse("/api/tickets/hold/validation", 404);

            assertBusinessException(
                    () -> ticketApiClient.validateTicket(
                            new ValidateTicketRequest(10L, 20L, 50_000L, "hold-key")
                    ),
                    OrderErrorCode.TICKET_NOT_FOUND
            );
        }

        @Test
        @DisplayName("404 이외의 통신 오류는 TICKET_VALIDATION_FAILED 예외로 변환한다")
        void serverError() {
            registerErrorResponse("/api/tickets/hold/validation", 500);

            assertBusinessException(
                    () -> ticketApiClient.validateTicket(
                            new ValidateTicketRequest(10L, 20L, 50_000L, "hold-key")
                    ),
                    OrderErrorCode.TICKET_VALIDATION_FAILED
            );
        }
    }

    @Nested
    @DisplayName("티켓 예약 확정")
    class ReserveTicket {

        @Test
        @DisplayName("예약 확정 API에 PUT 방식으로 요청한다")
        void success() {
            AtomicReference<String> method = new AtomicReference<>();
            AtomicReference<String> body = new AtomicReference<>();
            server.createContext("/api/tickets/status/reserved", exchange -> {
                method.set(exchange.getRequestMethod());
                body.set(readBody(exchange));
                respond(exchange, 200, "");
            });

            ticketApiClient.reserveTicket(
                    new TicketReserveRequest(10L, "hold-key", 20L)
            );

            assertThat(method.get()).isEqualTo("PUT");
            assertThat(body.get())
                    .contains("\"ticketId\":10")
                    .contains("\"holdKey\":\"hold-key\"")
                    .contains("\"userId\":20");
        }

        @Test
        @DisplayName("404 응답은 TICKET_NOT_FOUND 예외로 변환한다")
        void notFound() {
            registerErrorResponse("/api/tickets/status/reserved", 404);

            assertBusinessException(
                    () -> ticketApiClient.reserveTicket(
                            new TicketReserveRequest(10L, "hold-key", 20L)
                    ),
                    OrderErrorCode.TICKET_NOT_FOUND
            );
        }

        @Test
        @DisplayName("404 이외의 통신 오류는 TICKET_RESERVE_FAILED 예외로 변환한다")
        void serverError() {
            registerErrorResponse("/api/tickets/status/reserved", 500);

            assertBusinessException(
                    () -> ticketApiClient.reserveTicket(
                            new TicketReserveRequest(10L, "hold-key", 20L)
                    ),
                    OrderErrorCode.TICKET_RESERVE_FAILED
            );
        }
    }

    @Nested
    @DisplayName("티켓 점유 해제")
    class ReleaseSeat {

        @Test
        @DisplayName("점유 해제 API에 PUT 방식으로 요청한다")
        void success() {
            AtomicReference<String> method = new AtomicReference<>();
            AtomicReference<String> body = new AtomicReference<>();
            server.createContext("/api/tickets/status/release", exchange -> {
                method.set(exchange.getRequestMethod());
                body.set(readBody(exchange));
                respond(exchange, 200, "");
            });

            ticketApiClient.releaseSeat(new TicketReleaseRequest(10L, "hold-key"));

            assertThat(method.get()).isEqualTo("PUT");
            assertThat(body.get())
                    .contains("\"ticketId\":10")
                    .contains("\"holdKey\":\"hold-key\"");
        }

        @Test
        @DisplayName("404 응답은 TICKET_NOT_FOUND 예외로 변환한다")
        void notFound() {
            registerErrorResponse("/api/tickets/status/release", 404);

            assertBusinessException(
                    () -> ticketApiClient.releaseSeat(
                            new TicketReleaseRequest(10L, "hold-key")
                    ),
                    OrderErrorCode.TICKET_NOT_FOUND
            );
        }

        @Test
        @DisplayName("404 이외의 통신 오류는 TICKET_RELEASE_FAILED 예외로 변환한다")
        void serverError() {
            registerErrorResponse("/api/tickets/status/release", 500);

            assertBusinessException(
                    () -> ticketApiClient.releaseSeat(
                            new TicketReleaseRequest(10L, "hold-key")
                    ),
                    OrderErrorCode.TICKET_RELEASE_FAILED
            );
        }
    }

    @Nested
    @DisplayName("티켓 취소")
    class CancelTicket {

        @Test
        @DisplayName("취소 API에 PUT 방식으로 티켓과 사용자 정보를 전달한다")
        void success() {
            AtomicReference<String> method = new AtomicReference<>();
            AtomicReference<String> body = new AtomicReference<>();
            server.createContext("/api/tickets/status/canceled/release", exchange -> {
                method.set(exchange.getRequestMethod());
                body.set(readBody(exchange));
                respond(exchange, 200, "");
            });

            ticketApiClient.cancelTicket(new TicketCancelRequest(10L, 20L));

            assertThat(method.get()).isEqualTo("PUT");
            assertThat(body.get())
                    .contains("\"ticketId\":10")
                    .contains("\"userId\":20");
        }

        @Test
        @DisplayName("404 응답은 TICKET_NOT_FOUND 예외로 변환한다")
        void notFound() {
            registerErrorResponse("/api/tickets/status/canceled/release", 404);

            assertBusinessException(
                    () -> ticketApiClient.cancelTicket(new TicketCancelRequest(10L, 20L)),
                    OrderErrorCode.TICKET_NOT_FOUND
            );
        }

        @Test
        @DisplayName("404 이외의 통신 오류는 TICKET_RELEASE_FAILED 예외로 변환한다")
        void serverError() {
            registerErrorResponse("/api/tickets/status/canceled/release", 500);

            assertBusinessException(
                    () -> ticketApiClient.cancelTicket(new TicketCancelRequest(10L, 20L)),
                    OrderErrorCode.TICKET_RELEASE_FAILED
            );
        }
    }

    @Nested
    @DisplayName("주문 티켓 정보 조회")
    class GetTickets {

        @Test
        @DisplayName("조회 API 응답에서 티켓 목록을 반환한다")
        void success() {
            AtomicReference<String> method = new AtomicReference<>();
            AtomicReference<String> body = new AtomicReference<>();
            server.createContext("/api/tickets/orders", exchange -> {
                method.set(exchange.getRequestMethod());
                body.set(readBody(exchange));
                respond(exchange, 200, """
                        {"success":true,"data":[
                          {"ticketId":10,"performanceName":"오버타임 콘서트","zone":"VIP"}
                        ],"code":null,"message":null}
                        """);
            });

            List<TicketInfo> result = ticketApiClient.getTickets(new OrderTicketRequest(List.of(10L)));

            assertThat(method.get()).isEqualTo("POST");
            assertThat(body.get()).contains("\"ticketIds\":[10]");
            assertThat(result).containsExactly(
                    new TicketInfo(10L, "오버타임 콘서트", "VIP")
            );
        }

        @Test
        @DisplayName("응답 data가 null이면 TICKET_INFO_RESPONSE_EMPTY 예외가 발생한다")
        void emptyResponseData() {
            server.createContext("/api/tickets/orders", exchange -> respond(
                    exchange,
                    200,
                    "{\"success\":true,\"data\":null,\"code\":null,\"message\":null}"
            ));

            assertBusinessException(
                    () -> ticketApiClient.getTickets(new OrderTicketRequest(List.of(10L))),
                    OrderErrorCode.TICKET_INFO_RESPONSE_EMPTY
            );
        }

        @Test
        @DisplayName("통신 오류는 TICKET_INFO_GET_FAILED 예외로 변환한다")
        void serverError() {
            registerErrorResponse("/api/tickets/orders", 500);

            assertBusinessException(
                    () -> ticketApiClient.getTickets(new OrderTicketRequest(List.of(10L))),
                    OrderErrorCode.TICKET_INFO_GET_FAILED
            );
        }
    }

    private void registerErrorResponse(String path, int status) {
        server.createContext(path, exchange -> respond(exchange, status, ""));
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (!body.isEmpty()) {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
        }
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private void assertBusinessException(Runnable action, OrderErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
