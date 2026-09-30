package com.programmers.kdt.order.application.scheduler;

import com.programmers.kdt.order.domain.entity.TicketCancelJob;
import com.programmers.kdt.order.domain.entity.TicketCancelJobStatus;
import com.programmers.kdt.order.infrastructure.repository.TicketCancelJobRepository;
import com.programmers.kdt.order.application.service.TicketCancelJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketCancelJobSchedulerTest {

    @Mock
    private TicketCancelJobRepository ticketCancelJobRepository;
    @Mock
    private TicketCancelJobService ticketCancelJobService;
    @InjectMocks
    private TicketCancelJobScheduler scheduler;

    @Test
    @DisplayName("PENDING Job을 모두 조회해 취소 처리를 재시도한다")
    void retriesPendingJobs() {
        TicketCancelJob first = TicketCancelJob.create(1L, 10L, 100L);
        TicketCancelJob second = TicketCancelJob.create(2L, 20L, 200L);
        when(ticketCancelJobRepository.findAllByStatus(TicketCancelJobStatus.PENDING))
                .thenReturn(List.of(first, second));

        scheduler.cancelTicket();

        verify(ticketCancelJobService).process(1L, 10L, 100L);
        verify(ticketCancelJobService).process(2L, 20L, 200L);
    }
}
