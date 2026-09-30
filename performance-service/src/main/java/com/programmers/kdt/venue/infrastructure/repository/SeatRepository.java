package com.programmers.kdt.venue.infrastructure.repository;

import com.programmers.kdt.venue.domain.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    @Query("select distinct s.zone from Seat s where s.hallId = :hallId")
    List<String> findDistinctZoneByHallId(@Param("hallId") Long hallId);
}
