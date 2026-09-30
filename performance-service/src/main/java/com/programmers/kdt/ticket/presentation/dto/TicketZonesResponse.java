package com.programmers.kdt.ticket.presentation.dto;

import java.util.List;

public record TicketZonesResponse(
        String zone,
        Long price,
        List<TicketZoneResponse> sessionZones
) {
}
