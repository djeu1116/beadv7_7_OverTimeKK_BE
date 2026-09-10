package com.programmers.kdt.standby.entity;

import com.programmers.kdt.common.TimeLimits;
import com.programmers.kdt.common.entity.BaseTimeEntity;
import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.performance.entity.PerformanceSession;
import com.programmers.kdt.standby.exception.StandbyErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_standby_user_session",
                columnNames = {"user_id", "session_num", "performance_id"}),
        // findMatchCandidate()의 PESSIMISTIC_WRITE 조회(세션+상태별 reservedAt 오름차순 1건)를 위한 인덱스.
        // ddl-auto=validate라 Hibernate가 실제로 만들어주진 않음 - 실제 스키마는 init-databases.sql에서 관리.
        // 여기 선언은 "이 테이블엔 이 인덱스가 있어야 한다"는 의도를 코드에 남겨두는 문서화 목적.
        indexes = {
                @Index(
                        name = "idx_standby_match",
                        columnList = "performance_id, session_num, standby_status, reserved_at"),
                // NotificationReconciliationScheduler의 재시도 대상 조회(HELD + PENDING + modified_at 오름차순)용.
                @Index(
                        name = "idx_standby_notification_retry",
                        columnList = "standby_status, notification_status, modified_at")
        })
public class Standby extends BaseTimeEntity {

    private static final int MAX_ZONE_COUNT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long standbyId;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "session_num"),
            @JoinColumn(name = "performance_id")
    })
    private PerformanceSession performanceSession;

    private String zone1;

    private String zone2;

    private String zone3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StandbyStatus standbyStatus;

    // 어느 zone 슬롯(zone1/zone2/zone3)으로 매칭됐는지. 이름(slot)은 임시 확정이며 팀 논의 예정
    @Enumerated(EnumType.STRING)
    private Slot slot;

    // 판매자가 우선순위를 파악하기 위한 변수(1or2)
    private Long priority;

    // FIFO 기준이 되는 대기신청 시각
    private LocalDateTime reservedAt;
    // 결제 제한 마감 시각(매칭 시각 + 30분)
    private LocalDateTime expiredAt;

    private Long ticketId;

    // 매칭 알림(이메일) 발송 상태. HELD 전환 시 PENDING으로 시작, 재시도는 NotificationReconciliationScheduler가 담당.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus notificationStatus;

    private int notificationAttempts;

    public static Standby apply(Long userId, PerformanceSession session, List<String> zones){

        validateZones(zones);
        Standby standby = new Standby();
        standby.userId = userId;
        standby.performanceSession = session;
        standby.zone1 = zones.get(0);
        standby.zone2 = zones.size() > 1 ? zones.get(1) : null;
        standby.zone3 = zones.size() > 2 ? zones.get(2) : null;
        standby.standbyStatus = StandbyStatus.WAITING;
        standby.reservedAt = LocalDateTime.now();
        standby.notificationStatus = NotificationStatus.PENDING;
        return standby;
    }


    private static void validateZones(List<String> zones) {
        if (zones == null || zones.isEmpty() || zones.size() > MAX_ZONE_COUNT) {
            throw new BusinessException(StandbyErrorCode.INVALID_ZONE_COUNT);
        }
        long distinct = zones.stream().filter(Objects::nonNull).distinct().count();
        if (distinct != zones.size()) {
            throw new BusinessException(StandbyErrorCode.DUPLICATE_ZONE);
        }
    }

    public void hold(String zone, Long ticketId) {
        this.slot = resolveSlot(zone);
        this.standbyStatus = StandbyStatus.HELD;
        this.expiredAt = LocalDateTime.now().plusMinutes(TimeLimits.standbyHoldTicket30Min);
        this.ticketId = ticketId;
        this.notificationStatus = NotificationStatus.PENDING;
        this.notificationAttempts = 0;
    }

    public void markNotificationSent() {
        this.notificationStatus = NotificationStatus.SENT;
    }

    public void markNotificationFailed() {
        this.notificationAttempts++;
    }

    public void giveUpNotification() {
        this.notificationStatus = NotificationStatus.GAVE_UP;
    }

    private Slot resolveSlot(String zone) {
        if (Objects.equals(zone, zone1)) {
            return Slot.ZONE1;
        }
        if (Objects.equals(zone, zone2)) {
            return Slot.ZONE2;
        }
        if (Objects.equals(zone, zone3)) {
            return Slot.ZONE3;
        }
        throw new IllegalStateException("매칭 대상 zone이 이 대기 신청의 지망 목록에 없습니다.");
    }

    public void cancel() {
        if (isAlreadyCancelled()) {
            return;
        }
        guardCancellable();
        this.standbyStatus = StandbyStatus.CANCELLED;
    }

    private boolean isAlreadyCancelled() {
        return standbyStatus == StandbyStatus.CANCELLED;
    }

    private void guardCancellable() {
        if (standbyStatus != StandbyStatus.WAITING && standbyStatus != StandbyStatus.HELD) {
            throw new BusinessException(StandbyErrorCode.CANNOT_CANCEL);
        }
    }

    // 대기 순위 조회는 WAITING/HELD 상태에서만 허용 (취소된 신청 조회 불가)
    public boolean canViewRank() {
        return standbyStatus == StandbyStatus.WAITING || standbyStatus == StandbyStatus.HELD;
    }

    public String getMatchedZone() {
        if (slot == null) {
            return null;
        }
        return switch (slot) {
            case ZONE1 -> zone1;
            case ZONE2 -> zone2;
            case ZONE3 -> zone3;
        };
    }

    // 지망 zone 중 하나만 취소. 매칭됐던(slot) zone을 취소하면 남은 zone에 대해 WAITING으로 되돌아간다.
    // 남은 zone이 없으면(매칭 zone이었든 아니든) 전체 취소로 전환된다.
    public void cancelZone(String zone) {
        if (isAlreadyCancelled()) {
            return;
        }
        guardCancellable();

        boolean wasMatchedZone = Objects.equals(zone, getMatchedZone());
        removeZone(zone);

        if (wasMatchedZone) {
            this.slot = null;
            this.expiredAt = null;
            this.ticketId = null;
        }

        if (zone1 == null && zone2 == null && zone3 == null) {
            this.standbyStatus = StandbyStatus.CANCELLED;
        } else if (wasMatchedZone) {
            this.standbyStatus = StandbyStatus.WAITING;
        }
    }

    private void removeZone(String zone) {
        if (Objects.equals(zone, zone1)) {
            zone1 = null;
        } else if (Objects.equals(zone, zone2)) {
            zone2 = null;
        } else if (Objects.equals(zone, zone3)) {
            zone3 = null;
        } else {
            throw new BusinessException(StandbyErrorCode.ZONE_NOT_IN_STANDBY);
        }
    }
    
    public void reserve() {
        this.standbyStatus = StandbyStatus.RESERVED;
    }
}
