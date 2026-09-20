package com.programmers.kdt.order.api;

import com.programmers.kdt.order.entity.Order;
import com.programmers.kdt.order.entity.OrderItem;
import com.programmers.kdt.order.entity.OrderStatus;
import com.programmers.kdt.order.repository.OrderItemRepository;
import com.programmers.kdt.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentApiImplTest {

    private static final Long ORDER_ID = 1L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;

    private OrderPaymentApi orderPaymentApi;

    @BeforeEach
    void setUp() {
        orderPaymentApi = new OrderPaymentApiImpl(orderRepository, orderItemRepository);
    }

    private Order pendingOrder(LocalDateTime expiresAt) {
        return Order.create(10L, List.of(OrderItem.create(100L, 10000L, "hold-key")), expiresAt);
    }

    @Test
    @DisplayName("주문 스냅샷은 결제가 필요한 값만 담아서 반환한다.")
    void findOrderReturnsSnapshot() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(pendingOrder(LocalDateTime.now().plusMinutes(10))));

        OrderSnapshot snapshot = orderPaymentApi.findOrder(ORDER_ID).orElseThrow();

        assertThat(snapshot.userId()).isEqualTo(10L);
        assertThat(snapshot.totalAmount()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("없는 주문이면 빈 값을 반환한다.")
    void findOrderReturnsEmpty() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThat(orderPaymentApi.findOrder(ORDER_ID)).isEmpty();
    }

    @Test
    @DisplayName("상태 전이에 성공하면 STARTED를 반환한다.")
    void startPaymentStarted() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(pendingOrder(LocalDateTime.now().plusMinutes(10))));
        when(orderRepository.tryStartPayment(eq(ORDER_ID), eq(OrderStatus.PENDING), eq(OrderStatus.PAYMENT_STARTED), any(LocalDateTime.class)))
                .thenReturn(1);

        assertThat(orderPaymentApi.startPayment(ORDER_ID)).isEqualTo(StartPaymentResult.STARTED);
    }

    @Test
    @DisplayName("전이에 실패했고 만료 시각이 지났으면 EXPIRED를 반환한다.")
    void startPaymentExpired() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(pendingOrder(LocalDateTime.now().minusSeconds(1))));
        when(orderRepository.tryStartPayment(eq(ORDER_ID), eq(OrderStatus.PENDING), eq(OrderStatus.PAYMENT_STARTED), any(LocalDateTime.class)))
                .thenReturn(0);

        assertThat(orderPaymentApi.startPayment(ORDER_ID)).isEqualTo(StartPaymentResult.EXPIRED);
    }

    @Test
    @DisplayName("전이에 실패했지만 아직 만료 전이면 NOT_PENDING을 반환한다 - 이미 결제가 시작된 주문.")
    void startPaymentNotPending() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(pendingOrder(LocalDateTime.now().plusMinutes(10))));
        when(orderRepository.tryStartPayment(eq(ORDER_ID), eq(OrderStatus.PENDING), eq(OrderStatus.PAYMENT_STARTED), any(LocalDateTime.class)))
                .thenReturn(0);

        assertThat(orderPaymentApi.startPayment(ORDER_ID)).isEqualTo(StartPaymentResult.NOT_PENDING);
    }

    @Test
    @DisplayName("없는 주문이면 전이를 시도하지 않고 NOT_FOUND를 반환한다.")
    void startPaymentNotFound() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThat(orderPaymentApi.startPayment(ORDER_ID)).isEqualTo(StartPaymentResult.NOT_FOUND);
    }
}
