package com.programmers.kdt.performance.domain.entity;

import com.programmers.kdt.common.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_seat_price_performance_zone",
        columnNames = {"performance_id", "zone"}))
@Getter
@NoArgsConstructor
public class PerformanceSeatPrice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

//    @Column(nullable = false)
//    private Long hallId;

    @Column(nullable = false)
    private String zone;

    @Column(nullable = false)
    private Long price;

    public static PerformanceSeatPrice createInitial(Performance performance, String zone, Long price) {
        PerformanceSeatPrice seatPrice = new PerformanceSeatPrice();
        seatPrice.performance = performance;
        seatPrice.zone = zone;
        seatPrice.price = price;
        return seatPrice;
    }

}
