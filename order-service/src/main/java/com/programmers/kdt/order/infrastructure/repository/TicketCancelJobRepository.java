package com.programmers.kdt.order.infrastructure.repository;

import com.programmers.kdt.order.domain.entity.TicketCancelJob;
import com.programmers.kdt.order.domain.entity.TicketCancelJobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketCancelJobRepository extends JpaRepository<TicketCancelJob, Long> {

    Optional<TicketCancelJob> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    List<TicketCancelJob> findAllByStatus(TicketCancelJobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select j
            from TicketCancelJob j
            where j.status = :status
            """)
    List<TicketCancelJob> findAllByStatusForUpdate(@Param("status") TicketCancelJobStatus status);
}
