package com.programmers.kdt.standby.repository;

import com.programmers.kdt.performance.entity.PerformanceSession;
import com.programmers.kdt.standby.entity.NotificationStatus;
import com.programmers.kdt.standby.entity.Standby;
import com.programmers.kdt.standby.entity.StandbyStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StandbyRepository extends JpaRepository<Standby, Long> {
    List<Standby> findByPerformanceSessionOrderByReservedAtAsc(PerformanceSession session);

    boolean existsByUserIdAndPerformanceSession(Long userId, PerformanceSession performanceSession);

    @Query("""
        select s
        from Standby s
        join fetch s.performanceSession ps
        join fetch ps.performance
        where s.userId = :userId
        order by s.reservedAt desc
        """)
    List<Standby> findByUserIdOrderByReservedAtDesc(@Param("userId") Long userId);

    // zone을 지망(zone1/zone2/zone3)에 담아 대기 중인 사람 중 신청 순서(FIFO)가 가장 빠른 1명. 매칭 대상 선정용.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Standby s " +
            "where s.performanceSession = :session " +
            "and s.standbyStatus = :status " +
            "and (:zone = s.zone1 or :zone = s.zone2 or :zone = s.zone3) " +
            "order by s.reservedAt asc " +
            "limit 1")
    Optional<Standby> findMatchCandidate(@Param("session") PerformanceSession session,
                                          @Param("zone") String zone,
                                          @Param("status") StandbyStatus status);

    // cancelStandby/cancelZone 전용 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Standby s where s.standbyId = :standbyId")
    Optional<Standby> findByIdForUpdate(@Param("standbyId") Long standbyId);

    @Query("select count(s) from Standby s " +
            "where s.performanceSession = :session " +
            "and s.standbyStatus = :status " +
            "and (:zone = s.zone1 or :zone = s.zone2 or :zone = s.zone3) " +
            "and s.reservedAt < :reservedAt")
    long countRankCount(@Param("session") PerformanceSession session,
                        @Param("zone") String zone,
                        @Param("status") StandbyStatus status,
                        @Param("reservedAt") LocalDateTime reservedAt);


    List<Standby> findAllByStandbyStatusAndExpiredAtLessThanEqual(StandbyStatus standbyStatus, LocalDateTime expiredAt);
    Optional<Standby> findByTicketIdAndStandbyStatus(Long ticketId, StandbyStatus status);

    // 매칭 알림 발송 실패/미확인 건 재시도 대상 조회. NotificationReconciliationScheduler 전용.
    Page<Standby> findByStandbyStatusAndNotificationStatusAndModifiedAtBefore(
            StandbyStatus standbyStatus, NotificationStatus notificationStatus, LocalDateTime cutoff, Pageable pageable);
}
