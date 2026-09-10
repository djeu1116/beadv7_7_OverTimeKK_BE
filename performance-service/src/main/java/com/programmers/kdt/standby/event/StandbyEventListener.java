package com.programmers.kdt.standby.event;

import com.programmers.kdt.standby.client.UserClient;
import com.programmers.kdt.standby.service.StandbyService;
import com.programmers.kdt.ticket.event.StandbyCheckRequestEvent;
import com.programmers.kdt.ticket.event.StandbyTicketReservedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class StandbyEventListener {



    private final StandbyService standbyService;
    private final UserClient userClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCheckStandbyHandler(StandbyCheckRequestEvent event) {
        standbyService.StandbyCheck(event);
    }

    @EventListener void onTicketReservedHandler(StandbyTicketReservedEvent event) {
        standbyService.reservedStandby(event.ticketId());


    }
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void standbyMatchNotificationHandler(StandbyTicketEvent event) {
        String subject = "[ReSeat] 대기 매칭 완료 안내";
        String body = "결제 마감: " + event.standbyExpiredAt() + " 까지 결제해주세요.";
        try {
            userClient.sendMatchNotification(event.standbyUserId(), subject, body);
            standbyService.markNotificationSent(event.standbyId());
        } catch (Exception e) {
            log.warn("매칭 알림 발송 실패. standbyId={}, userId={}, ticketId={}", event.standbyId(), event.standbyUserId(), event.ticketId(), e);
            standbyService.markNotificationFailed(event.standbyId(), false);
        }
    }


}
