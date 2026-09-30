package com.programmers.kdt.payment.infrastructure.repository;

import com.programmers.kdt.payment.domain.entity.Payment;
import com.programmers.kdt.payment.domain.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Page<Payment> findByUserId(Long userId, Pageable pageable);

    boolean existsByOrderId(Long orderId);

    Optional<Payment> findByOrderId(Long orderId);

    Page<Payment> findByPaymentStatusAndModifiedAtBefore(PaymentStatus paymentStatus, LocalDateTime cutoffTime, Pageable pageable);

    Page<Payment> findByPaymentStatusInAndModifiedAtAfter(List<PaymentStatus> paymentStatuses, LocalDateTime since, Pageable pageable);
}
