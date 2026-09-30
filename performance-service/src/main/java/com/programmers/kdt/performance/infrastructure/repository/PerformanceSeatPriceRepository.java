package com.programmers.kdt.performance.infrastructure.repository;

import com.programmers.kdt.performance.domain.entity.PerformanceSeatPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PerformanceSeatPriceRepository extends JpaRepository<PerformanceSeatPrice, Long> {

    void deleteByPerformance_PerformanceId(Long performanceId);

    // 해당 공연이 실제 사용하는 좌석 구역(zone) 목록. 대기 신청 zone 유효성 검증용.
    @Query("select p.zone from PerformanceSeatPrice p where p.performance.performanceId = :performanceId")
    List<String> findZonesByPerformance_PerformanceId(@Param("performanceId") Long performanceId);

    PerformanceSeatPrice findByPerformance_PerformanceIdAndZone(Long performancePerformanceId, String zone);
}
