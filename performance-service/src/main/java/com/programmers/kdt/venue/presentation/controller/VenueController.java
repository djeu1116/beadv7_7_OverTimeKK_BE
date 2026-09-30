package com.programmers.kdt.venue.presentation.controller;

import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.venue.presentation.dto.HallResponse;
import com.programmers.kdt.venue.presentation.dto.VenueResponse;
import com.programmers.kdt.venue.application.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @GetMapping
    public ApiResponse<List<VenueResponse>> findVenues() {
        return ApiResponse.success(venueService.findVenues());
    }

    @GetMapping("/{venueId}/halls")
    public ApiResponse<List<HallResponse>> findHalls(@PathVariable Long venueId) {
        return ApiResponse.success(venueService.findHallsByVenueId(venueId));
    }

    @GetMapping("/halls/{hallId}/zones")
    public ApiResponse<List<String>> findZones(@PathVariable Long hallId) {
        return ApiResponse.success(venueService.findZonesByHallId(hallId));
    }
}
