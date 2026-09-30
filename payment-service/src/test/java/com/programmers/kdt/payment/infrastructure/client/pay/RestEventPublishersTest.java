package com.programmers.kdt.payment.infrastructure.client.pay;

import com.programmers.kdt.common.contract.CompensationCompletedEvent;
import com.programmers.kdt.common.contract.PaymentConfirmEvent;
import com.programmers.kdt.common.contract.PaymentFailEvent;
import com.programmers.kdt.common.contract.RefundCompletedEvent;
import com.programmers.kdt.common.contract.RefundFailedEvent;
import com.programmers.kdt.payment.infrastructure.client.refund.CompensationRequestEvent;
import com.programmers.kdt.payment.infrastructure.client.refund.RefundRequestEvent;
import com.programmers.kdt.payment.infrastructure.client.refund.RestRefundEventPublisher;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RestEventPublishersTest {

    private HttpServer server;
    private RestPaymentResultEventPublisher resultPublisher;
    private RestRefundEventPublisher refundPublisher;
    private final ApplicationEventPublisher inProcess = mock(ApplicationEventPublisher.class);
    private final List<String> paths = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private int status = 200;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        RestClient restClient = RestClient.create("http://localhost:" + server.getAddress().getPort());
        resultPublisher = new RestPaymentResultEventPublisher(restClient);
        refundPublisher = new RestRefundEventPublisher(restClient, inProcess);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("결제 결과 이벤트 2종은 각자의 내부 경로로 전달된다.")
    void paymentResultEventsArePostedToTheirPaths() {
        resultPublisher.publishConfirmed(new PaymentConfirmEvent(1L, 10L));
        resultPublisher.publishFailed(new PaymentFailEvent(2L, 20L, "PG_REQUEST_FAILED"));

        assertThat(paths).containsExactly("/internal/events/payment-confirmed", "/internal/events/payment-failed");
        assertThat(bodies.get(0)).contains("\"orderId\":1").contains("\"paymentId\":10");
        assertThat(bodies.get(1)).contains("PG_REQUEST_FAILED");
    }

    @Test
    @DisplayName("주문이 받는 환불 결과 3종은 HTTP로 전달된다.")
    void refundResultEventsArePostedToTheirPaths() {
        refundPublisher.publishCompleted(new RefundCompletedEvent(1L, 10L));
        refundPublisher.publishFailed(new RefundFailedEvent(2L, 20L, "REFUND_PERIOD_EXPIRED"));
        refundPublisher.publishCompensationCompleted(new CompensationCompletedEvent(3L, 30L));

        assertThat(paths).containsExactly(
                "/internal/events/refund-completed",
                "/internal/events/refund-failed",
                "/internal/events/compensation-completed");
        verifyNoInteractions(inProcess);
    }

    @Test
    @DisplayName("결제 안에서만 도는 환불 요청·보상 요청은 HTTP로 나가지 않고 프로세스 안에서 발행된다.")
    void paymentInternalEventsStayInProcess() {
        RefundRequestEvent refundRequested = new RefundRequestEvent(1L, "단순 변심", LocalDateTime.now());
        CompensationRequestEvent compensationRequested = new CompensationRequestEvent(1L);

        refundPublisher.publish(refundRequested);
        refundPublisher.publishCompensationRequested(compensationRequested);

        assertThat(paths).isEmpty();
        verify(inProcess).publishEvent(refundRequested);
        verify(inProcess).publishEvent(compensationRequested);
    }

    @Test
    @DisplayName("수신 측이 오류를 응답하면 예외를 던진다 - outbox 릴레이가 재시도하도록.")
    void throwsWhenReceiverFails() {
        status = 503;

        assertThatThrownBy(() -> resultPublisher.publishConfirmed(new PaymentConfirmEvent(1L, 10L)))
                .isInstanceOf(RestClientException.class);
    }
}
