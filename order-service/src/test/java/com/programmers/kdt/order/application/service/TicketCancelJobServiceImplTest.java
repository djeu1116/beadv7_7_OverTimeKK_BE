package com.programmers.kdt.order.application.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.order.infrastructure.client.TicketClient;
import com.programmers.kdt.order.presentation.dto.TicketCancelRequest;
import com.programmers.kdt.order.domain.entity.TicketCancelJob;
import com.programmers.kdt.order.domain.entity.TicketCancelJobStatus;
import com.programmers.kdt.order.domain.exception.OrderErrorCode;
import com.programmers.kdt.order.infrastructure.repository.TicketCancelJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketCancelJobServiceImplTest {

    private static final Long ORDER_ID = 1L;
    private static final Long TICKET_ID = 10L;
    private static final Long USER_ID = 100L;

    @Mock
    private TicketClient ticketClient;
    @Mock
    private TicketCancelJobRepository ticketCancelJobRepository;

    private TicketCancelJobService service;
    private TicketCancelJob job;

    @BeforeEach
    void setUp() {
        service = new TicketCancelJobServiceImpl(ticketClient, ticketCancelJobRepository);
        job = TicketCancelJob.create(ORDER_ID, TICKET_ID, USER_ID);
    }

    @Test
    @DisplayName("티켓 취소가 성공하면 Job을 DONE으로 처리한다")
    void success() {
        when(ticketCancelJobRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(job));

        service.process(ORDER_ID, TICKET_ID, USER_ID);

        verify(ticketClient).cancelTicket(new TicketCancelRequest(TICKET_ID, USER_ID));
        assertThat(job.getStatus()).isEqualTo(TicketCancelJobStatus.DONE);
    }

    @Test
    @DisplayName("티켓 해제가 실패하면 실패를 기록하고 PENDING을 유지한다")
    void releaseFailure() {
        doThrow(new BusinessException(OrderErrorCode.TICKET_RELEASE_FAILED))
                .when(ticketClient).cancelTicket(new TicketCancelRequest(TICKET_ID, USER_ID));
        when(ticketCancelJobRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(job));

        service.process(ORDER_ID, TICKET_ID, USER_ID);

        assertThat(job.getStatus()).isEqualTo(TicketCancelJobStatus.PENDING);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getLastError()).isEqualTo(OrderErrorCode.TICKET_RELEASE_FAILED.getCode());
    }

    @Test
    @DisplayName("재시도 대상이 아닌 오류는 Job을 DONE으로 처리한다")
    void nonRetryableFailure() {
        doThrow(new BusinessException(OrderErrorCode.TICKET_NOT_FOUND))
                .when(ticketClient).cancelTicket(new TicketCancelRequest(TICKET_ID, USER_ID));
        when(ticketCancelJobRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(job));

        service.process(ORDER_ID, TICKET_ID, USER_ID);

        assertThat(job.getStatus()).isEqualTo(TicketCancelJobStatus.DONE);
    }
}
