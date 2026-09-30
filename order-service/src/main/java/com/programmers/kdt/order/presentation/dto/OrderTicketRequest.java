package com.programmers.kdt.order.presentation.dto;

import java.util.List;

public record OrderTicketRequest(
        List<Long> ticketIds
) {
}
