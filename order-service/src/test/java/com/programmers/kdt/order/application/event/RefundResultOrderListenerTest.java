package com.programmers.kdt.order.application.event;

import com.programmers.kdt.order.application.service.OrderService;
import com.programmers.kdt.common.contract.RefundCompletedEvent;
import com.programmers.kdt.common.contract.RefundFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundResultOrderListenerTest {

    @Mock
    private OrderService orderService;

    private RefundResultOrderListener listener;

    @BeforeEach
    void setUp() {
        listener = new RefundResultOrderListener(orderService);
    }

    @Test
    @DisplayName("환불 완료 이벤트를 받으면 주문 취소를 확정한다")
    void handleCompleted() {
        RefundCompletedEvent event = new RefundCompletedEvent(1L, 10L);

        listener.handleCompleted(event);

        verify(orderService).confirmCancellation(1L);
    }

    @Test
    @DisplayName("환불 실패 이벤트를 받으면 주문 취소 접수를 복구한다")
    void handleFailed() {
        RefundFailedEvent event = new RefundFailedEvent(1L, 10L, "PG_REQUEST_FAILED");

        listener.handleFailed(event);

        verify(orderService).revertCancellation(1L);
    }
}
