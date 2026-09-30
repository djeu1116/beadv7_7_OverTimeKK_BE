package com.programmers.kdt.order.infrastructure.repository;

import com.programmers.kdt.order.domain.entity.Order;
import com.programmers.kdt.order.domain.entity.OrderItem;
import com.programmers.kdt.order.domain.entity.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OrderRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 25, 12, 0);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("만료 전 PENDING 주문만 결제 시작 상태로 변경한다")
    void tryStartPaymentBeforeExpiration() {
        Order order = savePendingOrder(NOW.plusSeconds(1));

        int updatedRows = tryStartPayment(order.getOrderId());

        assertThat(updatedRows).isEqualTo(1);
        assertThat(findStatus(order.getOrderId())).isEqualTo(OrderStatus.PAYMENT_STARTED);
    }

    @Test
    @DisplayName("만료 시각과 같거나 지난 PENDING 주문은 결제를 시작하지 않는다")
    void tryStartPaymentAtOrAfterExpiration() {
        Order expiresAtNow = savePendingOrder(NOW);
        Order alreadyExpired = savePendingOrder(NOW.minusSeconds(1));

        assertThat(tryStartPayment(expiresAtNow.getOrderId())).isZero();
        assertThat(tryStartPayment(alreadyExpired.getOrderId())).isZero();
        assertThat(findStatus(expiresAtNow.getOrderId())).isEqualTo(OrderStatus.PENDING);
        assertThat(findStatus(alreadyExpired.getOrderId())).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("만료 시각과 같거나 지난 PENDING 주문만 만료 상태로 변경한다")
    void tryExpireAtOrAfterExpiration() {
        Order expiresAtNow = savePendingOrder(NOW);
        Order alreadyExpired = savePendingOrder(NOW.minusSeconds(1));
        Order notExpired = savePendingOrder(NOW.plusSeconds(1));

        assertThat(tryExpire(expiresAtNow.getOrderId())).isEqualTo(1);
        assertThat(tryExpire(alreadyExpired.getOrderId())).isEqualTo(1);
        assertThat(tryExpire(notExpired.getOrderId())).isZero();
        assertThat(findStatus(expiresAtNow.getOrderId())).isEqualTo(OrderStatus.EXPIRED);
        assertThat(findStatus(alreadyExpired.getOrderId())).isEqualTo(OrderStatus.EXPIRED);
        assertThat(findStatus(notExpired.getOrderId())).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("이미 PENDING을 벗어난 주문은 결제 시작하거나 만료하지 않는다")
    void conditionalUpdatesRequirePendingStatus() {
        Order paymentStarted = savePendingOrder(NOW.plusMinutes(1));
        paymentStarted.startPayment(NOW);
        Order expired = savePendingOrder(NOW.minusMinutes(1));
        expired.expire();
        orderRepository.flush();

        assertThat(tryStartPayment(paymentStarted.getOrderId())).isZero();
        assertThat(tryExpire(paymentStarted.getOrderId())).isZero();
        assertThat(tryStartPayment(expired.getOrderId())).isZero();
        assertThat(tryExpire(expired.getOrderId())).isZero();
        assertThat(findStatus(paymentStarted.getOrderId())).isEqualTo(OrderStatus.PAYMENT_STARTED);
        assertThat(findStatus(expired.getOrderId())).isEqualTo(OrderStatus.EXPIRED);
    }

    private Order savePendingOrder(LocalDateTime expiresAt) {
        Order order = Order.create(
                1L,
                List.of(OrderItem.create(100L, 50_000L, "hold-key")),
                expiresAt
        );
        return orderRepository.saveAndFlush(order);
    }

    private int tryStartPayment(Long orderId) {
        return orderRepository.tryStartPayment(
                orderId,
                OrderStatus.PENDING,
                OrderStatus.PAYMENT_STARTED,
                NOW
        );
    }

    private int tryExpire(Long orderId) {
        return orderRepository.tryExpire(
                orderId,
                OrderStatus.PENDING,
                OrderStatus.EXPIRED,
                NOW
        );
    }

    private OrderStatus findStatus(Long orderId) {
        entityManager.clear();
        return orderRepository.findById(orderId).orElseThrow().getOrderStatus();
    }
}
