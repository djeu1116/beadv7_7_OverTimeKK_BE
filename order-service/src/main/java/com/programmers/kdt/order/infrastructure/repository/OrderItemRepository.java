package com.programmers.kdt.order.infrastructure.repository;

import com.programmers.kdt.order.application.api.PointEarnTargetView;
import com.programmers.kdt.order.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    @Query("select oi from OrderItem oi where oi.order.orderId = :orderId")
    Optional<OrderItem> findFirstByOrderId(@Param("orderId") Long orderId);

    @Query("""
    SELECT new com.programmers.kdt.order.application.api.PointEarnTargetView(o.userId, oi.ticketId, oi.ticketPrice)
    FROM OrderItem oi
    JOIN oi.order o
    WHERE oi.ticketId IN :ticketIds
    AND o.orderStatus = com.programmers.kdt.order.domain.entity.OrderStatus.COMPLETED
    """)
    List<PointEarnTargetView> findPointEarnTargets(@Param("ticketIds") List<Long> ticketIds);
}
