package com.programmers.kdt.order.application.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.order.infrastructure.client.TicketClient;
import com.programmers.kdt.order.presentation.dto.TicketCancelRequest;
import com.programmers.kdt.order.domain.entity.TicketCancelJob;
import com.programmers.kdt.order.domain.exception.OrderErrorCode;
import com.programmers.kdt.order.infrastructure.repository.TicketCancelJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketCancelJobServiceImpl implements TicketCancelJobService {

    private final TicketClient ticketClient;
    private final TicketCancelJobRepository ticketCancelJobRepository;

    @Override
    @Transactional
    public void process(Long orderId, Long ticketId, Long userId) {
        try{
            ticketClient.cancelTicket(new TicketCancelRequest(ticketId, userId));
            markDone(orderId);
        } catch(BusinessException e){
            if(OrderErrorCode.TICKET_RELEASE_FAILED.equals(e.getErrorCode())) {
                recordFailure(orderId, e.getErrorCode().getCode());
                return;
            }
            markDone(orderId);
        }
    }

    @Override
    public void markDone(Long orderId) {
        ticketCancelJobRepository.findByOrderId(orderId)
                .ifPresent(TicketCancelJob::markDone);
    }

    @Override
    public void recordFailure(Long orderId, String error) {
        ticketCancelJobRepository.findByOrderId(orderId)
                .ifPresent(job -> job.recordFailure(error));
    }
}
