package com.programmers.kdt.ticket.application.event;

import com.programmers.kdt.ticket.domain.event.TicketIssueEvent;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.ticket.domain.exception.TicketErrorCode;
import com.programmers.kdt.ticket.infrastructure.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TicketIssueEventListener {

    private final TicketRepository ticketRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTicketIssue(TicketIssueEvent event) {
        int issueTicketCount = ticketRepository.issueTicket(event.performanceId(), event.ticketStatus());
        if (issueTicketCount <= 0) {
            throw new BusinessException(TicketErrorCode.TICKET_ISSUE_FAILED);
        }

        // TODO : 티켓 발행 취소 했을 때의 보상 트랜잭션 작성 필요
    }
}