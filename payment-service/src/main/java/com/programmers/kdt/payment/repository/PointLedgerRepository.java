package com.programmers.kdt.payment.repository;

import com.programmers.kdt.payment.entity.PointLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointLedgerRepository extends JpaRepository<PointLedger, Long> {
    Optional<PointLedger> findByUseLogId(Long useLogId);
}
