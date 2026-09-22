package com.programmers.kdt.payment.client.point;

import java.time.LocalDate;
import java.util.List;

public record EndedTicketsResponse(
        LocalDate from,
        LocalDate to,
        List<EndedTicket> endedTickets
) {
}
