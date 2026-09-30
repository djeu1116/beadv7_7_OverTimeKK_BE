package com.programmers.kdt.ticket.application.service.impl;

import com.programmers.kdt.performance.infrastructure.repository.PerformanceSeatPriceRepository;
import com.programmers.kdt.performance.infrastructure.repository.PerformanceSessionRepository;
import com.programmers.kdt.ticket.domain.entity.Ticket;
import com.programmers.kdt.ticket.domain.entity.TicketStatus;
import com.programmers.kdt.ticket.infrastructure.repository.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private PerformanceSessionRepository sessionRepository;
    @Mock private PerformanceSeatPriceRepository performanceSeatPriceRepository;

    @InjectMocks private TicketServiceImpl ticketService;

    private static Ticket holdTicket(Long ticketId, Long userId, LocalDateTime holdExpiredAt, String holdKey) {
        Ticket ticket = Ticket.create(1L, 1L, "A", "1", String.valueOf(ticketId), 10000L);
        ReflectionTestUtils.setField(ticket, "ticketId", ticketId);
        ticket.holdTicket(userId, holdExpiredAt, holdKey);
        return ticket;
    }

    @Test
    @DisplayName("점유시간이 지난 HOLD 티켓의 id 목록을 반환한다.")
    void returnsExpiredTicketIds() {
        Ticket ticket = holdTicket(1L, 100L, LocalDateTime.now().minusMinutes(1), "hold-key");
        when(ticketRepository.findByTicketStatusAndHoldExpiredAtBefore(eq(TicketStatus.HOLD), any()))
                .thenReturn(List.of(ticket));

        List<Long> result = ticketService.findExpiredHoldTicketIds();

        assertThat(result).containsExactly(1L);
    }

    @Test
    @DisplayName("점유시간이 지난 HOLD 티켓이 없으면 빈 목록을 반환한다.")
    void noExpiredTickets_returnsEmpty() {
        when(ticketRepository.findByTicketStatusAndHoldExpiredAtBefore(eq(TicketStatus.HOLD), any()))
                .thenReturn(List.of());

        assertThat(ticketService.findExpiredHoldTicketIds()).isEmpty();
    }
}