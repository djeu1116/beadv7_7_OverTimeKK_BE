package com.programmers.kdt.payment.client.point;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Profile("!performance-api")
@Component
public class MockEndedPerformanceClient implements EndedPerformanceClient {
    @Override
    public List<EndedTicket> findEndedTickets(LocalDate from, LocalDate to) {
        return List.of();
    }
}
