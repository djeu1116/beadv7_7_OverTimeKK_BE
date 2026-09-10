package com.programmers.kdt.standby.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.performance.entity.PerformanceSession;
import com.programmers.kdt.performance.entity.PerformanceSessionId;
import com.programmers.kdt.performance.exception.PerformanceErrorCode;
import com.programmers.kdt.performance.repository.PerformanceSeatPriceRepository;
import com.programmers.kdt.performance.repository.PerformanceSessionRepository;
import com.programmers.kdt.standby.dto.CreateStandbyResponse;
import com.programmers.kdt.standby.dto.StandbyListResponse;
import com.programmers.kdt.standby.dto.StandbyRankResponse;
import com.programmers.kdt.standby.entity.Standby;
import com.programmers.kdt.standby.entity.StandbyStatus;
import com.programmers.kdt.standby.event.StandbyCheckResponseEvent;
import com.programmers.kdt.standby.event.StandbyTicketEvent;
import com.programmers.kdt.standby.exception.StandbyErrorCode;
import com.programmers.kdt.standby.repository.StandbyRepository;
import com.programmers.kdt.ticket.event.StandbyCheckRequestEvent;
import com.programmers.kdt.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional
public class StandbyService {

    private final StandbyRepository standbyRepository;
    private final PerformanceSessionRepository performanceSessionRepository;
    private final PerformanceSeatPriceRepository performanceSeatPriceRepository;
    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;


    public CreateStandbyResponse applyStandby(Long userId, Long performanceId, Long sessionNum, List<String> zones) {
        PerformanceSession session = findSession(performanceId, sessionNum);

        if (standbyRepository.existsByUserIdAndPerformanceSession(userId, session)) {
            throw new BusinessException(StandbyErrorCode.ALREADY_APPLIED);
        }

        validateZonesUsedByPerformance(performanceId, zones);
        validateAllZonesSoldOut(performanceId, sessionNum, zones);

        Standby standby = Standby.apply(userId, session, zones);
        Long standbyId = standbyRepository.save(standby).getStandbyId();
        return new CreateStandbyResponse(standbyId, zones, StandbyStatus.WAITING.name());
    }

    public StandbyRankResponse getStandbyRank (Long standbyId, Long userId) {
        Standby standby = findOwnedStandby(standbyId, userId);
        if (!standby.canViewRank()) {
            throw new BusinessException(StandbyErrorCode.CANNOT_VIEW_RANK);
        }
        List<String> zones = Stream.of(standby.getZone1(),standby.getZone2(),standby.getZone3())
                .filter(Objects::nonNull)
                .toList();
        String matchedZone = standby.getMatchedZone();
        //  쿼리로 순위 각각 조회
        List<StandbyRankResponse.ZoneRank> zoneRanks = new ArrayList<>();
        for(String zone : zones) {
            if (zone.equals(matchedZone)) {
                zoneRanks.add(new StandbyRankResponse.ZoneRank(zone, 0, true, standby.getTicketId()));
                continue;
            }
            long rankCount = standbyRepository.countRankCount(
                    standby.getPerformanceSession(), zone, StandbyStatus.WAITING, standby.getReservedAt());
            zoneRanks.add(new StandbyRankResponse.ZoneRank(zone, rankCount+1, false, null)
            );

        }
        return new StandbyRankResponse(standby.getStandbyId(),zoneRanks);
    }

    public List<StandbyListResponse> getStandby(Long userId) {
        return standbyRepository.findByUserIdOrderByReservedAtDesc(userId).stream()
                .map(this::toStandbyListResponse)
                .toList();
    }

    private StandbyListResponse toStandbyListResponse(Standby standby) {
        PerformanceSession session = standby.getPerformanceSession();
        List<String> zones = Stream.of(standby.getZone1(), standby.getZone2(), standby.getZone3())
                .filter(Objects::nonNull)
                .toList();

        return new StandbyListResponse(
                standby.getStandbyId(),
                session.getPerformanceSessionId().getPerformanceId(),
                session.getPerformanceSessionId().getSessionNum(),
                session.getPerformance().getTitle(),
                session.getPerformanceStartAt(),
                zones,
                standby.getMatchedZone(),
                standby.getTicketId(),
                standby.getStandbyStatus().name(),
                standby.getReservedAt(),
                standby.getExpiredAt()
        );
    }

    private void validateZonesUsedByPerformance(Long performanceId, List<String> zones) {
        List<String> usableZones = performanceSeatPriceRepository
                .findZonesByPerformance_PerformanceId(performanceId);
        if (!usableZones.containsAll(zones)) {
            throw new BusinessException(StandbyErrorCode.INVALID_ZONE);
        }
    }

    // 요청 zone이 모두 매진 상태여야 대기 신청 가능.
    private void validateAllZonesSoldOut(Long performanceId, Long sessionNum, List<String> zones) {
        List<String> availableZones = ticketRepository
                .findAvailableZones(performanceId, sessionNum, zones);
        if (!availableZones.isEmpty()) {
            throw new BusinessException(StandbyErrorCode.ZONE_NOT_SOLD_OUT);
        }
    }
   public void StandbyCheck (StandbyCheckRequestEvent event) {
        PerformanceSession session = findSession(event.performanceId(),event.sessionNum());
        boolean existsStandby = matchNextCandidate(session, event.zone(), event.ticketId()).isPresent();
        eventPublisher.publishEvent(new StandbyCheckResponseEvent(event.ticketId(), existsStandby));
    }

    // 매칭 성사 시 hold() 처리와 ticket 알림
    // 재매칭(취소로 인한)에서는 취소된 standby가 들고 있던 값을 그대로 이어받는다.
    private Optional<Standby> matchNextCandidate(PerformanceSession session, String zone, Long ticketId) {
        return standbyRepository
                .findMatchCandidate(session, zone, StandbyStatus.WAITING)
                .map(matched -> {
                    matched.hold(zone, ticketId);
                    eventPublisher.publishEvent(
                            new StandbyTicketEvent(matched.getStandbyId(), ticketId, matched.getUserId(), matched.getExpiredAt()));
                    return matched;
                });
    }

    // 매칭 알림 발송 결과 반영. 리스너(@Async)/재조회 스케줄러 양쪽에서 호출하므로 독립 트랜잭션으로 처리.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markNotificationSent(Long standbyId) {
        standbyRepository.findById(standbyId).ifPresent(Standby::markNotificationSent);
    }

    // giveUp=true면 재시도를 포기하고 GAVE_UP으로 확정 (재조회 스케줄러의 재시도 시간 초과 판단 결과).
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markNotificationFailed(Long standbyId, boolean giveUp) {
        standbyRepository.findById(standbyId).ifPresent(standby -> {
            standby.markNotificationFailed();
            if (giveUp) {
                standby.giveUpNotification();
            }
        });
    }

    private PerformanceSession findSession(Long performanceId, Long sessionNum) {
        return performanceSessionRepository
                .findById(new PerformanceSessionId(sessionNum, performanceId))
                .orElseThrow(() -> new BusinessException(PerformanceErrorCode.PERFORMANCE_SESSION_NOT_FOUND));
    }

    // 본인의 WAITING/HELD 대기를 취소. HELD 상태였다면 재매칭을 커밋 이후 이벤트로 위임한다
    // (한 트랜잭션에서 락 2개를 동시에 쥐면 데드락이 나서, 트랜잭션을 분리함).
    public void cancelStandby(Long standbyId, Long userId) {
        Standby standby = findOwnedStandbyForUpdate(standbyId, userId);

        boolean wasHeld = standby.getStandbyStatus() == StandbyStatus.HELD;
        String matchedZone = standby.getMatchedZone();
        Long ticketId = standby.getTicketId();
        PerformanceSession session = standby.getPerformanceSession();

        standby.cancel();

        if (wasHeld) {
            requestRematch(session, matchedZone, ticketId);
        }
    }

    // 지망 zone 중 하나만 취소. 매칭돼있던(HELD) zone이면 cancelStandby와 동일하게 재매칭을 이벤트로 위임.
    public void cancelZone(Long standbyId, Long userId, String zone) {
        Standby standby = findOwnedStandbyForUpdate(standbyId, userId);

        boolean wasMatchedZone = zone.equals(standby.getMatchedZone());
        Long ticketId = standby.getTicketId();
        PerformanceSession session = standby.getPerformanceSession();

        standby.cancelZone(zone);

        if (wasMatchedZone) {
            requestRematch(session, zone, ticketId);
        }
    }

    // 티켓취소 경로가 쓰는 매칭 요청 이벤트를 재사용 (AFTER_COMMIT + REQUIRES_NEW로 처리됨)
    private void requestRematch(PerformanceSession session, String zone, Long ticketId) {
        PerformanceSessionId sessionId = session.getPerformanceSessionId();
        eventPublisher.publishEvent(
                new StandbyCheckRequestEvent(sessionId.getPerformanceId(), sessionId.getSessionNum(), zone, ticketId));
    }

    private Standby findOwnedStandby(Long standbyId, Long userId) {
        Standby standby = standbyRepository.findById(standbyId)
                .orElseThrow(() -> new BusinessException(StandbyErrorCode.STANDBY_NOT_FOUND));

        if (!standby.getUserId().equals(userId)) {
            throw new BusinessException(StandbyErrorCode.NOT_STANDBY_OWNER);
        }
        return standby;
    }

    // cancelStandby/cancelZone 전용 락 조회
    private Standby findOwnedStandbyForUpdate(Long standbyId, Long userId) {
        Standby standby = standbyRepository.findByIdForUpdate(standbyId)
                .orElseThrow(() -> new BusinessException(StandbyErrorCode.STANDBY_NOT_FOUND));

        if (!standby.getUserId().equals(userId)) {
            throw new BusinessException(StandbyErrorCode.NOT_STANDBY_OWNER);
        }
        return standby;
    }

    // 매칭(HELD)후 결제 제한시간(30분)이 지난 건 취소
    public int expireHeldStandbys() {
        List<Standby> expiredStandbys = standbyRepository
                .findAllByStandbyStatusAndExpiredAtLessThanEqual(StandbyStatus.HELD, LocalDateTime.now());

        for (Standby standby : expiredStandbys) {
            String matchedZone = standby.getMatchedZone();
            Long ticketId = standby.getTicketId();
            PerformanceSession session = standby.getPerformanceSession();

            standby.cancelZone(matchedZone);
            matchNextCandidate(session, matchedZone, ticketId);
        }
        return expiredStandbys.size();
    }

    //매칭결제준비
    public void reservedStandby(Long ticketId) {
        Optional<Standby> heldStandby = standbyRepository.findByTicketIdAndStandbyStatus(ticketId,StandbyStatus.HELD);
        if(heldStandby.isPresent()) {
            Standby standby = heldStandby.get();
            standby.reserve();
        }
    }
}
