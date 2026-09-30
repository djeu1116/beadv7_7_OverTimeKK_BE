package com.programmers.kdt.venue.domain.entity;

import com.programmers.kdt.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "seat",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_seat",
                        columnNames = {"hall_id", "zone", "seat_row", "seat_num"}
                )
        }
)
public class Seat extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;

    @Column(name = "hall_id")
    private Long hallId;

    @Column(name = "zone", nullable = false)
    private String zone;

    @Column(name = "seat_row")
    private String seatRow;

    @Column(name = "seat_num")
    private String seatNum;
}
