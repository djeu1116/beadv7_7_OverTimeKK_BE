package com.programmers.kdt.standby.presentation.dto;

import java.util.List;

public record StandbyRankResponse (
        Long standbyId,
        List<ZoneRank> zoneRanks
)

{
    public record ZoneRank(String zone, long rank, boolean isHeld, Long ticketId) {}
}
