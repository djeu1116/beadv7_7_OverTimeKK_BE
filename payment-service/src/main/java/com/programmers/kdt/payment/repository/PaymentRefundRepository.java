package com.programmers.kdt.payment.repository;

import com.programmers.kdt.payment.entity.PaymentRefund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

    Page<PaymentRefund> findByPaymentId(Long paymentId, Pageable pageable);
}
