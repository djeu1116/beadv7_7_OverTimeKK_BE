package com.programmers.kdt.performance.domain.entity;

import com.programmers.kdt.common.entity.BaseTimeEntity;
import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.performance.domain.exception.PerformanceErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class Performance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "performance_id")
    private Long performanceId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "runtime", nullable = false)
    private Long runtime;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // 티켓 오픈 시각 (ERD: 티켓오픈시간 LocalDateTime NULL)
    @Column(name = "ticket_open_at")
    private LocalDateTime ticketOpenAt;

    // user-service의 판매자(User)를 ID로만 참조
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "hall_id", nullable = false)
    private Long hallId;

    @Column(name = "img_path")
    private String imgPath;

    private PerformanceStatus performanceStatus = PerformanceStatus.UPCOMING;

    public static Performance createPerformance(String title, String description, Long runtime,
                                                LocalDate startDate, LocalDate endDate, LocalDateTime ticketOpenAt,
                                                Long sellerId, Long hallId, String imgPath) {
        validatePerformance(startDate, endDate, ticketOpenAt);
        Performance performance = new Performance();
        performance.title = title;
        performance.description = description;
        performance.runtime = runtime;
        performance.startDate = startDate;
        performance.endDate = endDate;
        performance.ticketOpenAt = ticketOpenAt;
        performance.sellerId = sellerId;
        performance.hallId = hallId;
        performance.performanceStatus = PerformanceStatus.UPCOMING;
        performance.imgPath = imgPath;
        return performance;
    }

    // TODO: 티켓 오픈(ticketOpenAt) 이후에는 판매자가 좌석가(PerformanceSeatPrice) 변경 불가.
    //  좌석가격 수정 로직 추후 추가 및 검증 필요
    public void updatePerformance(String title, String description, Long runtime,
                                  LocalDate startDate, LocalDate endDate, LocalDateTime ticketOpenAt, Long hallId) {
        validatePerformance(startDate, endDate, ticketOpenAt);
        this.title = title;
        this.description = description;
        this.runtime = runtime;
        this.startDate = startDate;
        this.endDate = endDate;
        this.ticketOpenAt = ticketOpenAt;
        this.hallId = hallId;
    }

    private static void validatePerformance(LocalDate startDate, LocalDate endDate, LocalDateTime ticketOpenAt) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(PerformanceErrorCode.INVALID_PERIOD);
        }
        if (ticketOpenAt != null && ticketOpenAt.toLocalDate().isAfter(startDate)) {
            throw new BusinessException(PerformanceErrorCode.INVALID_TICKET_OPEN);
        }
    }

}