package com.programmers.kdt.payment.repository;

import com.programmers.kdt.payment.entity.PaymentAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    Optional<PaymentAttempt> findByPaymentIdAndAttemptSeq(Long paymentId, Integer attemptSeq);

    Page<PaymentAttempt> findByPaymentIdOrderByAttemptSeqDesc(Long paymentId, Pageable pageable);
}
