package com.programmers.kdt.payment.infrastructure.repository;

import com.programmers.kdt.payment.domain.entity.Point;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointRepository extends JpaRepository<Point, Long> {
}
