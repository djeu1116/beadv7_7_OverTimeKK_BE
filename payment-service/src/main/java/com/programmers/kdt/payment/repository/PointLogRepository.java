package com.programmers.kdt.payment.repository;

import com.programmers.kdt.payment.entity.PointLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PointLogRepository extends JpaRepository<PointLog, Long> {
    List<PointLog> findByUserId(Long userId);

    Optional<PointLog> findByEventId(String eventId);

    List<PointLog> findByUserIdOrderByCreatedAtAsc(Long userId);


}
