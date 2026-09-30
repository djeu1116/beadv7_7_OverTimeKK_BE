package com.programmers.kdt.performance.infrastructure.repository;

import com.programmers.kdt.performance.domain.entity.PerformanceSession;
import com.programmers.kdt.performance.domain.entity.PerformanceSessionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PerformanceSessionRepository extends JpaRepository<PerformanceSession, PerformanceSessionId> {
    List<PerformanceSession> findByPerformanceSessionId_PerformanceId(Long performanceId);

    void deleteByPerformanceSessionId_PerformanceId(Long performanceId);
}
