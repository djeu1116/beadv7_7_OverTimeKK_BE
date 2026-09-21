package com.programmers.kdt.order.service;

import com.programmers.kdt.order.client.TicketClient;
import com.programmers.kdt.order.dto.CancelOrderRequest;
import com.programmers.kdt.order.entity.Order;
import com.programmers.kdt.order.entity.OrderItem;
import com.programmers.kdt.order.entity.OrderStatus;
import com.programmers.kdt.order.entity.outbox.OrderOutboxEventStatus;
import com.programmers.kdt.order.repository.OrderItemRepository;
import com.programmers.kdt.order.repository.OrderOutboxEventRepository;
import com.programmers.kdt.order.repository.OrderRepository;
import com.programmers.kdt.order.scheduler.OrderOutboxRelayScheduler;
import com.programmers.kdt.payment.entity.Payment;
import com.programmers.kdt.payment.entity.PaymentStatus;
import com.programmers.kdt.payment.entity.outbox.OutboxEventStatus;
import com.programmers.kdt.payment.repository.OutboxEventRepository;
import com.programmers.kdt.payment.repository.PaymentRepository;
import com.programmers.kdt.payment.scheduler.OutboxRelayScheduler;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// 주문/결제가 같은 프로세스에 있어도 두 프로필을 켜면 서로를 HTTP(loopback)로만 호출한다.
// 서비스를 분리했을 때와 같은 경로로 취소 흐름 전체가 완결되는지 확인한다.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=18082",
                "order-service.url=http://localhost:18082",
                "payment-service.url=http://localhost:18082",
                "spring.datasource.url=jdbc:h2:mem:remote-flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.task.scheduling.enabled=false",
                "user-service.url=http://localhost:8081",
                "performance-service.url=http://localhost:8083",
                "jwt.secret=dev-only-secret-key-please-change-in-real-deployment-32bytes+",
                "jwt.expiration-millis=1800000",
                "jwt.refresh-expiration-millis=604800000",
                "management.otlp.metrics.export.enabled=false"
        })
@ActiveProfiles({"order-api", "payment-api"})
@Import(OrderPaymentRemoteFlowIntegrationTest.AlwaysGrantedLockConfig.class)
class OrderPaymentRemoteFlowIntegrationTest {

    // 릴레이의 ShedLock이 Redis에 붙지 않도록 항상 락을 허용하는 제공자로 대체한다. 락 동작은 이 테스트의 관심사가 아님.
    @TestConfiguration
    static class AlwaysGrantedLockConfig {
        @Bean
        @Primary
        LockProvider testLockProvider() {
            return lockConfiguration -> Optional.of((SimpleLock) () -> { });
        }
    }

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private OrderOutboxEventRepository orderOutboxEventRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private OrderOutboxRelayScheduler orderOutboxRelay;
    @Autowired
    private OutboxRelayScheduler paymentOutboxRelay;

    @MockitoBean
    private TicketClient ticketClient;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        orderOutboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("주문 취소가 주문 outbox -> HTTP -> 결제 환불 -> 결제 outbox -> HTTP -> 주문 확정까지 이어진다.")
    void cancelFlowCompletesOverHttp() {
        Long orderId = saveCompletedOrder();
        Payment payment = savePaidPayment(orderId);

        orderService.cancelCompletedOrder(orderId, 1L, new CancelOrderRequest("단순 변심"));
        assertThat(orderRepository.findById(orderId).orElseThrow().getOrderStatus())
                .isEqualTo(OrderStatus.CANCEL_REQUESTED);

        // 1) 주문 outbox 릴레이 -> HTTP -> 결제가 환불을 접수
        orderOutboxRelay.relay();
        assertThat(orderOutboxEventRepository.findAll())
                .singleElement()
                .satisfies(event -> assertThat(event.getStatus()).isEqualTo(OrderOutboxEventStatus.SENT));
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.REFUND_PENDING);

        // 2) 결제 outbox 릴레이 -> PG 취소 -> REFUND_COMPLETED 기록
        paymentOutboxRelay.relay();
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.CANCELLED);

        // 3) 다음 틱: REFUND_COMPLETED -> HTTP -> 주문이 취소 확정
        paymentOutboxRelay.relay();
        assertThat(orderRepository.findById(orderId).orElseThrow().getOrderStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(outboxEventRepository.findAll())
                .allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT));
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

    private Payment savePaidPayment(Long orderId) {
        Payment payment = Payment.create(orderId, 1L, 50_000L);
        payment.assignPaymentKey("PG_KEY_REMOTE");
        payment.approve();
        return paymentRepository.saveAndFlush(payment);
    }
}
