package com.programmers.kdt.ticket.application.event;

import com.programmers.kdt.ticket.domain.event.TicketIssueEvent;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.ticket.domain.entity.TicketStatus;
import com.programmers.kdt.ticket.infrastructure.repository.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketIssueEventListenerTest {

    @Mock private TicketRepository ticketRepository;
    @InjectMocks private TicketIssueEventListener listener;

    @Test
    @DisplayName("티켓이 정상 발급되면 예외가 발생하지 않는다.")
    void handleTicketIssue_success() {
        when(ticketRepository.issueTicket(1L, TicketStatus.AVAILABLE)).thenReturn(10);

        listener.handleTicketIssue(new TicketIssueEvent(1L, TicketStatus.AVAILABLE));
    }

    @Test
    @DisplayName("티켓이 하나도 발급되지 않으면 예외가 발생한다.")
    void handleTicketIssue_fail() {
        when(ticketRepository.issueTicket(1L, TicketStatus.AVAILABLE)).thenReturn(0);

        assertThatThrownBy(() -> listener.handleTicketIssue(new TicketIssueEvent(1L, TicketStatus.AVAILABLE)))
                .isInstanceOf(BusinessException.class);
    }
}