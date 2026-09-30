package com.programmers.kdt.performance.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class PerformanceSessionId implements Serializable {

    @Column(nullable = false)
    private Long sessionNum;

    @Column(nullable = false)
    private Long performanceId;
}
