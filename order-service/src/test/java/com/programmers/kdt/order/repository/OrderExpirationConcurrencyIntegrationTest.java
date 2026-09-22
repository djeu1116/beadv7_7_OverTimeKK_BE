package com.programmers.kdt.order.repository;

import com.programmers.kdt.order.client.TicketClient;
import com.programmers.kdt.order.entity.Order;
import com.programmers.kdt.order.entity.OrderItem;
import com.programmers.kdt.order.entity.OrderStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-expiration-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class OrderExpirationConcurrencyIntegrationTest {

    private static final LocalDateTime EXPIRES_AT =
            LocalDateTime.of(2099, 1, 1, 12, 0);
    private static final LocalDateTime PAYMENT_NOW = EXPIRES_AT.minusSeconds(1);
    private static final LocalDateTime EXPIRATION_NOW = EXPIRES_AT;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private TicketClient ticketClient;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (executor != null) {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @RepeatedTest(10)
    @DisplayName("결제 시작과 주문 만료가 동시에 PENDING 주문을 변경하면 한쪽만 성공한다")
    void startPaymentAndExpirationCompeteForSameOrder() throws Exception {
        Long orderId = saveCommittedPendingOrder();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> paymentResult = executor.submit(() -> executeInTransaction(
                ready,
                start,
                () -> orderRepository.tryStartPayment(
                        orderId,
                        OrderStatus.PENDING,
                        OrderStatus.PAYMENT_STARTED,
                        PAYMENT_NOW
                )
        ));
        Future<Integer> expirationResult = executor.submit(() -> executeInTransaction(
                ready,
                start,
                () -> orderRepository.tryExpire(
                        orderId,
                        OrderStatus.PENDING,
                        OrderStatus.EXPIRED,
                        EXPIRATION_NOW
                )
        ));

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        int paymentUpdatedRows = paymentResult.get(10, TimeUnit.SECONDS);
        int expirationUpdatedRows = expirationResult.get(10, TimeUnit.SECONDS);
        OrderStatus finalStatus = orderRepository.findById(orderId)
                .orElseThrow()
                .getOrderStatus();

        assertThat(paymentUpdatedRows + expirationUpdatedRows).isEqualTo(1);
        if (paymentUpdatedRows == 1) {
            assertThat(finalStatus).isEqualTo(OrderStatus.PAYMENT_STARTED);
        } else {
            assertThat(expirationUpdatedRows).isEqualTo(1);
            assertThat(finalStatus).isEqualTo(OrderStatus.EXPIRED);
        }
    }

    private Long saveCommittedPendingOrder() {
        return transactionTemplate.execute(status -> {
            Order order = Order.create(
                    1L,
                    List.of(OrderItem.create(100L, 50_000L, "hold-key")),
                    EXPIRES_AT
            );
            return orderRepository.saveAndFlush(order).getOrderId();
        });
    }

    private int executeInTransaction(
            CountDownLatch ready,
            CountDownLatch start,
            UpdateOperation operation
    ) {
        Integer result = transactionTemplate.execute(status -> {
            ready.countDown();
            await(start);
            return operation.execute();
        });
        if (result == null) {
            throw new IllegalStateException("조건부 UPDATE 결과가 없습니다.");
        }
        return result;
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @FunctionalInterface
    private interface UpdateOperation {
        int execute();
    }
}
