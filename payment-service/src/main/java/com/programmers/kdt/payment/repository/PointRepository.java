package com.programmers.kdt.payment.repository;

import com.programmers.kdt.payment.entity.Point;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointRepository extends JpaRepository<Point, Long> {
}
