package com.programmers.kdt.payment.repository;

import com.programmers.kdt.payment.entity.PointLog;
import com.programmers.kdt.payment.entity.PointType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PointLogRepository extends JpaRepository<PointLog, Long> {
    List<PointLog> findByUserId(Long userId);

    Optional<PointLog> findByEventId(String eventId);

    List<PointLog> findByUserIdOrderByCreatedAtAsc(Long userId);

    Page<PointLog> findByPointType(PointType pointType, Pageable pageable);

    // 원장 불변식 검증용 - 사용 건(원본 로그) 하나에 대해 실제로 기록된 환급 로그 합계
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PointLog p WHERE p.refLogId = :refLogId AND p.pointType IN :pointTypes")
    Long sumAmountByRefLogIdAndPointTypeIn(@Param("refLogId") Long refLogId, @Param("pointTypes") List<PointType> pointTypes);
}
