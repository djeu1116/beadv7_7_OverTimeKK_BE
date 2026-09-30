package com.programmers.kdt.ticket.domain.event;

import com.programmers.kdt.ticket.domain.entity.TicketStatus;

public record TicketIssueEvent(Long performanceId, TicketStatus ticketStatus) {
}