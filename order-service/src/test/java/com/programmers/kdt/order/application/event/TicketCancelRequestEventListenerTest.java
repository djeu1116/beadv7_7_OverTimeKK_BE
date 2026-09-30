package com.programmers.kdt.order.application.event;

import com.programmers.kdt.order.domain.event.TicketCancelRequestEvent;

import com.programmers.kdt.order.application.service.TicketCancelJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketCancelRequestEventListenerTest {

    @Mock
    private TicketCancelJobService ticketCancelJobService;

    private TicketCancelRequestEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TicketCancelRequestEventListener(ticketCancelJobService);
    }

    @Test
    @DisplayName("티켓 취소 이벤트를 DB Job 처리 서비스에 위임한다")
    void delegatesToJobService() {
        TicketCancelRequestEvent event = new TicketCancelRequestEvent(10L, 900001L, 1L);

        listener.handle(event);

        verify(ticketCancelJobService).process(1L, 10L, 900001L);
    }
}
