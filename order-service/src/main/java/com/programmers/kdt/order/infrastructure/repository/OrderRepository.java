package com.programmers.kdt.order.infrastructure.repository;

import com.programmers.kdt.order.domain.entity.Order;
import com.programmers.kdt.order.domain.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByOrderStatusAndExpiresAtLessThanEqual(
            OrderStatus orderStatus,
            LocalDateTime expiresAt
    );

    @Modifying
    @Query("""
        update Order o
          set o.orderStatus = :nextStatus
        where o.orderId = :orderId
          and o.orderStatus = :currentStatus
          and o.expiresAt > :now
        """)
    int tryStartPayment(
            @Param("orderId") Long orderId,
            @Param("currentStatus") OrderStatus currentStatus,
            @Param("nextStatus") OrderStatus nextStatus,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
    update Order o
       set o.orderStatus = :nextStatus
     where o.orderId = :orderId
       and o.orderStatus = :currentStatus
       and o.expiresAt <= :now
    """)
    int tryExpire(
            @Param("orderId") Long orderId,
            @Param("currentStatus") OrderStatus currentStatus,
            @Param("nextStatus") OrderStatus nextStatus,
            @Param("now") LocalDateTime now
    );

    @Query("""
    select o.orderId
      from Order o
     where o.orderStatus = :status
       and o.expiresAt <= :now
     order by o.expiresAt asc, o.orderId asc
    """)
    List<Long> findExpirationCandidateIds(
            @Param("status") OrderStatus status,
            @Param("now") LocalDateTime now
    );

    List<Order> findByUserIdAndOrderStatusInOrderByCreatedAtDesc(
            Long userId,
            Collection<OrderStatus> orderStatuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select distinct o
        from Order o
        left join fetch o.items
        where o.orderId = :orderId
        """)
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    @Query("""
        select distinct o
        from Order o
        join fetch o.items item
        where item.ticketId in :ticketIds
        """)
    List<Order> findAllByTicketIds(
            @Param("ticketIds") List<Long> ticketIds
    );
}
