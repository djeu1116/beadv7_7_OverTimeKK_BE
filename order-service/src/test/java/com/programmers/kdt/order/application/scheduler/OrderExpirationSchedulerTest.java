package com.programmers.kdt.order.application.scheduler;

import com.programmers.kdt.order.domain.entity.OrderStatus;
import com.programmers.kdt.order.infrastructure.repository.OrderRepository;
import com.programmers.kdt.order.application.service.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExpirationSchedulerTest {

    @Mock
    private OrderServiceImpl orderService;
    @Mock
    private OrderRepository orderRepository;

    private OrderExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OrderExpirationScheduler(orderService, orderRepository);
    }

    @Test
    @DisplayName("조회한 모든 만료 후보를 동일한 기준 시각으로 처리한다")
    void expiresAllCandidatesWithSameNow() {
        when(orderRepository.findExpirationCandidateIds(eqStatus(), any(LocalDateTime.class)))
                .thenReturn(List.of(1L, 2L));

        scheduler.expireOrder();

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderService).expireOrder(org.mockito.ArgumentMatchers.eq(1L), nowCaptor.capture());
        verify(orderService).expireOrder(org.mockito.ArgumentMatchers.eq(2L), nowCaptor.capture());
        assertThat(nowCaptor.getAllValues()).hasSize(2).allMatch(nowCaptor.getValue()::equals);
    }

    @Test
    @DisplayName("한 주문 만료가 실패해도 다음 주문 처리를 계속한다")
    void continuesAfterOneOrderFails() {
        when(orderRepository.findExpirationCandidateIds(eqStatus(), any(LocalDateTime.class)))
                .thenReturn(List.of(1L, 2L));
        doThrow(new RuntimeException("expiration failure"))
                .when(orderService).expireOrder(org.mockito.ArgumentMatchers.eq(1L), any(LocalDateTime.class));

        scheduler.expireOrder();

        var ordered = inOrder(orderService);
        ordered.verify(orderService).expireOrder(org.mockito.ArgumentMatchers.eq(1L), any(LocalDateTime.class));
        ordered.verify(orderService).expireOrder(org.mockito.ArgumentMatchers.eq(2L), any(LocalDateTime.class));
    }

    private OrderStatus eqStatus() {
        return org.mockito.ArgumentMatchers.eq(OrderStatus.PENDING);
    }
}
