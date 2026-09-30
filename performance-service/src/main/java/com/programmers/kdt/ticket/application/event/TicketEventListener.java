package com.programmers.kdt.ticket.application.event;

import com.programmers.kdt.standby.domain.event.StandbyCheckResponseEvent;
import com.programmers.kdt.standby.domain.event.StandbyTicketEvent;
import com.programmers.kdt.ticket.application.service.TicketReleaseService;
import com.programmers.kdt.ticket.application.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TicketEventListener {

    private final TicketService ticketService;
    private final TicketReleaseService ticketReleaseService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStandbyTicketHandler(StandbyTicketEvent event) {
        ticketService.standbyTicket(event);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCheckStandbyResultHandler(StandbyCheckResponseEvent event) {
        ticketReleaseService.changeTicketStatusByStandby(event);
    }
}