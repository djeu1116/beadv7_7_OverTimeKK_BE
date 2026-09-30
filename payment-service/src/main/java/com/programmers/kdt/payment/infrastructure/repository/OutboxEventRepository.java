package com.programmers.kdt.payment.infrastructure.repository;

import com.programmers.kdt.payment.domain.entity.outbox.OutboxEvent;
import com.programmers.kdt.payment.domain.entity.outbox.OutboxEventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    Page<OutboxEvent> findByStatusAndNextRetryAtLessThanEqual(OutboxEventStatus status, LocalDateTime now, Pageable pageable);
}
