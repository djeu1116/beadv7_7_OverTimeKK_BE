package com.programmers.kdt.payment.scheduler;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.client.point.EndedPerformanceClient;
import com.programmers.kdt.payment.client.point.EndedTicket;
import com.programmers.kdt.payment.dto.PointEarnTarget;
import com.programmers.kdt.payment.exception.PointErrorCode;
import com.programmers.kdt.payment.client.order.PointEarnTargetClient;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskType;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskWriter;
import com.programmers.kdt.payment.service.PointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointEarnScheduler {

    private static final double EARN_RATE = 0.01; // 티켓 가격의 1% 포인트 지급
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int LOOKBACK_DAYS = 3; // 배치 실패 대비 재조회 범위

    private final EndedPerformanceClient endedPerformanceClient;
    private final PointEarnTargetClient pointEarnTargetClient;
    private final PointService pointService;
    private final ReconciliationTaskWriter reconciliationTaskWriter;

    @Scheduled(cron = "0 01 0 * * *")
    public void earnPointsForEndedPerformances() {
        LocalDate to = LocalDate.now().minusDays(1);
        LocalDate from = to.minusDays(LOOKBACK_DAYS - 1);
        earnPointsForEndedPerformances(from, to);
    }

    public void earnPointsForEndedPerformances(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) { // 관리자 수동 지급시 잘못된 날짜 범위 입력 방지
            throw new BusinessException(PointErrorCode.INVALID_DATE_RANGE, from, to);
        }
        try {
            List<EndedTicket> endedTickets = endedPerformanceClient.findEndedTickets(from, to);
            if (endedTickets.isEmpty()) {
                log.info("{}~{} 종료된 공연의 정산 대상 티켓 없음", from, to);
                return;
            }

            List<Long> ticketIds = endedTickets.stream().map(EndedTicket::ticketId).toList();
            List<PointEarnTarget> targets = pointEarnTargetClient.findEarnTargets(ticketIds);

            int successCount = 0;
            for (PointEarnTarget target : targets) {
                try {
                    long earnAmount = Math.round(target.ticketPrice() * EARN_RATE);
                    if (earnAmount <= 0) {
                        continue;
                    }
                    if (earnPointWithRetry(target, earnAmount)) {
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("포인트 적립 처리 중 예상치 못한 예외 - ticketId={}, userId={}", target.ticketId(), target.userId(), e);
                }
            }
            log.info("{}~{} 공연 종료 포인트 적립 대상 {}건 중 {}건 처리 완료", from, to, targets.size(), successCount);
        } catch (Exception e) {
            log.error("[POINT_EARN_BATCH_FAILED] {}~{} 포인트 적립 배치 자체가 실패. 자동 재처리 되지 않으니 수동 확인 필요.", from, to, e);
            // 배치 단위 실패라 특정 대상 하나로 좁혀지지 않음 - aggregateId는 관례상 0
            reconciliationTaskWriter.record(ReconciliationTaskType.POINT_EARN_BATCH_FAILED, 0L,
                    "period=" + from + "~" + to + ", error=" + e.getMessage());
        }
    }

    private boolean earnPointWithRetry(PointEarnTarget target, long earnAmount) {
        String eventId = pointEarnEventId(target.ticketId());

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                pointService.earnPoint(target.userId(), earnAmount, eventId);
                return true;
            } catch (BusinessException e) {
                boolean retryable = e.getErrorCode() == PointErrorCode.POINT_CONCURRENT_MODIFICATION;
                if (!retryable) {
                    log.error("포인트 적립 실패(재시도 불가) - ticketId={}, userId={}, errorCode={}",
                            target.ticketId(), target.userId(), e.getErrorCode(), e);
                    return false;
                }
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    log.error("[POINT_EARN_RECONCILIATION_NEEDED] 동시성 충돌로 재시도 소진 - ticketId={}, userId={}, amount={}",
                            target.ticketId(), target.userId(), earnAmount, e);
                    reconciliationTaskWriter.record(ReconciliationTaskType.POINT_EARN_FAILED, target.ticketId(), earnAmount,
                            "userId=" + target.userId());
                    return false;
                }
                log.warn("포인트 적립 동시성 충돌, 재시도 {}회차 - ticketId={}", attempt, target.ticketId());
            }
        }
        return false;
    }

    private String pointEarnEventId(Long ticketId) {
        return "TICKET:" + ticketId + ":POINT_EARN";
    }
}
