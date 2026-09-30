package com.programmers.kdt.order.application.scheduler;

import com.programmers.kdt.order.domain.entity.TicketCancelJob;
import com.programmers.kdt.order.domain.entity.TicketCancelJobStatus;
import com.programmers.kdt.order.infrastructure.repository.TicketCancelJobRepository;
import com.programmers.kdt.order.application.service.TicketCancelJobService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class TicketCancelJobScheduler {

    private final TicketCancelJobRepository ticketCancelJobRepository;
    private final TicketCancelJobService ticketCancelJobService;

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "ticketCancelJob", lockAtMostFor = "2m", lockAtLeastFor = "10s")
    public void cancelTicket(){
        for(TicketCancelJob job : ticketCancelJobRepository.findAllByStatus(TicketCancelJobStatus.PENDING)){
            ticketCancelJobService.process(job.getOrderId(), job.getTicketId(), job.getUserId());
        }
    }
}
