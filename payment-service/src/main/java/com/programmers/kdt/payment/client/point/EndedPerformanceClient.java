package com.programmers.kdt.payment.client.point;

import java.time.LocalDate;
import java.util.List;

public interface EndedPerformanceClient {
    List<EndedTicket> findEndedTickets(LocalDate from, LocalDate to);
}
