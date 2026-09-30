package com.programmers.kdt.order.application.service;

public interface TicketCancelJobService {

    void process(Long orderId, Long ticketId, Long userId);

    void markDone(Long orderId);

    void recordFailure(Long orderId, String error);
}
