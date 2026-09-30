package com.programmers.kdt.order.application.event;

import com.programmers.kdt.order.domain.event.TicketReleaseRequestEvent;

import com.programmers.kdt.order.infrastructure.client.TicketClient;
import com.programmers.kdt.order.presentation.dto.TicketReleaseRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketReleaseRequestEventListener {

    private final TicketClient ticketClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TicketReleaseRequestEvent event){
        try{
            ticketClient.releaseSeat(new TicketReleaseRequest(event.ticketId(), event.holdKey()));
        } catch (RestClientException e){
            log.error(
                    "좌석 해제 요청 실패 :  orderId={}, ticketId={}",
                    event.orderId(),
                    event.ticketId(),
                    e
            );
        }
    }
}
