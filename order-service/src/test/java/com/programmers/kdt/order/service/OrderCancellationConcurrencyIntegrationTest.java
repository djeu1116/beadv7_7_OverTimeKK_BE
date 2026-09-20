package com.programmers.kdt.order.service;

import com.programmers.kdt.order.client.TicketClient;
import com.programmers.kdt.order.dto.CancelOrderRequest;
import com.programmers.kdt.order.dto.CancelOrderResponse;
import com.programmers.kdt.order.entity.Order;
import com.programmers.kdt.order.entity.OrderItem;
import com.programmers.kdt.order.entity.OrderStatus;
import com.programmers.kdt.order.repository.OrderItemRepository;
import com.programmers.kdt.order.repository.OrderRepository;
import com.programmers.kdt.common.contract.RefundCompletedEvent;
import com.programmers.kdt.common.contract.RefundFailedEvent;
import com.programmers.kdt.order.entity.outbox.OrderOutboxEventStatus;
import com.programmers.kdt.order.repository.OrderOutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-cancellation;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.task.scheduling.enabled=false",
        "user-service.url=http://localhost:8081",
        "order-service.url=http://localhost:8082",
        "performance-service.url=http://localhost:8083",
        "jwt.secret=dev-only-secret-key-please-change-in-real-deployment-32bytes+",
        "jwt.expiration-millis=1800000",
        "jwt.refresh-expiration-millis=604800000",
        "management.otlp.metrics.export.enabled=false"
})
class OrderCancellationConcurrencyIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private OrderOutboxEventRepository orderOutboxEventRepository;

    @MockitoBean
    private TicketClient ticketClient;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        reset(ticketClient);
        orderOutboxEventRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (executor != null) {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("동시에 취소를 요청해도 환불은 한 번만 접수한다")
    void concurrentCancelRequestsSubmitRefundOnce() throws Exception {
        Long orderId = saveCompletedOrder();
        CountDownLatch start = new CountDownLatch(1);

        Future<CancelOrderResponse> first = executor.submit(() -> {
            start.await();
            return orderService.cancelCompletedOrder(orderId, 1L, new CancelOrderRequest("단순 변심"));
        });
        Future<CancelOrderResponse> second = executor.submit(() -> {
            start.await();
            return orderService.cancelCompletedOrder(orderId, 1L, new CancelOrderRequest("단순 변심"));
        });

        start.countDown();

        assertThat(first.get(10, TimeUnit.SECONDS).orderStatus())
                .isEqualTo(OrderStatus.CANCEL_REQUESTED.name());
        assertThat(second.get(10, TimeUnit.SECONDS).orderStatus())
                .isEqualTo(OrderStatus.CANCEL_REQUESTED.name());
        assertThat(orderRepository.findById(orderId).orElseThrow().getOrderStatus())
                .isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(orderOutboxEventRepository.findAll())
                .singleElement()
                .satisfies(event -> assertThat(event.getStatus()).isEqualTo(OrderOutboxEventStatus.PENDING));
        verify(ticketClient, never()).cancelTicket(any());
    }

    @Test
    @DisplayName("환불 완료 이벤트를 동시에 받아도 티켓 취소는 한 번만 요청한다")
    void concurrentRefundCompletedEventsCancelTicketOnce() throws Exception {
        Long orderId = saveCancelRequestedOrder();

        publishConcurrently(
                new RefundCompletedEvent(orderId, 10L),
                new RefundCompletedEvent(orderId, 10L)
        );

        assertThat(orderRepository.findById(orderId).orElseThrow().getOrderStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        verify(ticketClient, times(1)).cancelTicket(any());
    }

    @Test
    @DisplayName("환불 실패 이벤트를 동시에 받아도 주문은 COMPLETED로 복구되고 티켓을 취소하지 않는다")
    void concurrentRefundFailedEventsKeepTicketReserved() throws Exception {
        Long orderId = saveCancelRequestedOrder();

        publishConcurrently(
                new RefundFailedEvent(orderId, 10L, "PG_REQUEST_FAILED"),
                new RefundFailedEvent(orderId, 10L, "PG_REQUEST_FAILED")
        );

        assertThat(orderRepository.findById(orderId).orElseThrow().getOrderStatus())
                .isEqualTo(OrderStatus.COMPLETED);
        verify(ticketClient, never()).cancelTicket(any());
    }

    private Long saveCompletedOrder() {
        Order order = Order.create(
                1L,
                List.of(OrderItem.create(10L, 50_000L, "hold-key")),
                LocalDateTime.now().plusMinutes(10)
        );
        order.startPayment(LocalDateTime.now());
        order.complete();
        return orderRepository.saveAndFlush(order).getOrderId();
    }

    private Long saveCancelRequestedOrder() {
        Long orderId = saveCompletedOrder();
        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow();
            order.requestCancel();
        });
        return orderId;
    }

    private void publishConcurrently(Object firstEvent, Object secondEvent) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Future<?> first = executor.submit(() -> publishAfterStart(start, firstEvent));
        Future<?> second = executor.submit(() -> publishAfterStart(start, secondEvent));

        start.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
    }

    private void publishAfterStart(CountDownLatch start, Object event) {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }
}
