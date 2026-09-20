package com.programmers.kdt.order.repository;

import com.programmers.kdt.order.entity.outbox.OrderOutboxEvent;
import com.programmers.kdt.order.entity.outbox.OrderOutboxEventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface OrderOutboxEventRepository extends JpaRepository<OrderOutboxEvent, Long> {
    Page<OrderOutboxEvent> findByStatusAndNextRetryAtLessThanEqual(OrderOutboxEventStatus status, LocalDateTime now, Pageable pageable);
}
