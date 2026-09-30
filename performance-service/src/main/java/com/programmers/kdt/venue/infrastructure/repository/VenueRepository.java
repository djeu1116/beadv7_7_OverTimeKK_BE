package com.programmers.kdt.venue.infrastructure.repository;

import com.programmers.kdt.venue.presentation.dto.VenueResponse;
import com.programmers.kdt.venue.domain.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    @Query("select new com.programmers.kdt.venue.presentation.dto.VenueResponse(v.venueId, v.venueName) from Venue v")
    List<VenueResponse> findAllVenues();
}
