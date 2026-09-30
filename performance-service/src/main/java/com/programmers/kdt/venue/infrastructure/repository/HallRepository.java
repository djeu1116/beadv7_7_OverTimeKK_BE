package com.programmers.kdt.venue.infrastructure.repository;

import com.programmers.kdt.venue.presentation.dto.HallResponse;
import com.programmers.kdt.venue.domain.entity.Hall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HallRepository extends JpaRepository<Hall, Long> {

    @Query("select new com.programmers.kdt.venue.presentation.dto.HallResponse(h.hallId, h.hallName) " +
            "from Hall h where h.venue.venueId = :venueId")
    List<HallResponse> findHallsByVenueId(@Param("venueId") Long venueId);
}
