package com.programmers.kdt.standby.scheduler;

import com.programmers.kdt.standby.client.SlackAlertClient;
import com.programmers.kdt.standby.client.UserClient;
import com.programmers.kdt.standby.entity.NotificationStatus;
import com.programmers.kdt.standby.entity.Standby;
import com.programmers.kdt.standby.entity.StandbyStatus;
import com.programmers.kdt.standby.repository.StandbyRepository;
import com.programmers.kdt.standby.service.StandbyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

// 매칭 성사 직후 발송되는 알림(이벤트 리스너, fire-and-forget)이 실패했을 때의 재시도 담당.
// PaymentReconciliationScheduler와 동일한 폴링 재조회 패턴 재사용 - 여기서는 "발송 결과 불확실성"이 아니라
// "외부 API 호출 실패에 대한 at-least-once 재전송 보장"이 목적.
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationReconciliationScheduler {

    private static final int BATCH_SIZE = 50;
    private static final Duration MIN_PENDING_AGE = Duration.ofSeconds(60);
    private static final Duration GIVE_UP_THRESHOLD = Duration.ofMinutes(10);

    private final StandbyRepository standbyRepository;
    private final StandbyService standbyService;
    private final UserClient userClient;
    private final SlackAlertClient slackAlertClient;

    @Scheduled(fixedDelay = 60000)
    public void reconcileNotifications() {
        LocalDateTime cutoffTime = LocalDateTime.now().minus(MIN_PENDING_AGE);
        Page<Standby> targets = standbyRepository.findByStandbyStatusAndNotificationStatusAndModifiedAtBefore(
                StandbyStatus.HELD,
                NotificationStatus.PENDING,
                cutoffTime,
                PageRequest.of(0, BATCH_SIZE, Sort.by("modifiedAt").ascending())
        );
        if (targets.isEmpty()) return;

        int resolved = 0;
        for (Standby standby : targets) {
            try {
                if (reconcile(standby)) resolved++;
            } catch (Exception e) {
                log.error("매칭 알림 재발송 처리 중 예상치 못한 예외 - standbyId={}", standby.getStandbyId(), e);
            }
        }
        log.info("매칭 알림 재발송 대상 {}건 중 {}건 처리", targets.getNumberOfElements(), resolved);
    }

    // true 반환 = 이번 건은 더 이상 재시도 큐에 남지 않음(발송 성공 or 포기 확정)
    private boolean reconcile(Standby standby) {
        if (attemptSend(standby)) {
            standbyService.markNotificationSent(standby.getStandbyId());
            return true;
        }

        Duration pending = Duration.between(standby.getModifiedAt(), LocalDateTime.now());
        boolean giveUp = pending.compareTo(GIVE_UP_THRESHOLD) >= 0;
        standbyService.markNotificationFailed(standby.getStandbyId(), giveUp);

        if (giveUp) {
            String alertMessage = "[STANDBY_NOTIFICATION_RECONCILIATION_NEEDED] 재시도 시간 초과로 매칭 알림 발송 포기 - standbyId=%d, userId=%d, pendingSince=%s"
                    .formatted(standby.getStandbyId(), standby.getUserId(), standby.getModifiedAt());
            log.error(alertMessage);
            slackAlertClient.sendAlert(alertMessage);
        }
        return giveUp;
    }

    private boolean attemptSend(Standby standby) {
        try {
            String subject = "[ReSeat] 대기 매칭 완료 안내";
            String body = "결제 마감: " + standby.getExpiredAt() + " 까지 결제해주세요.";
            userClient.sendMatchNotification(standby.getUserId(), subject, body);
            return true;
        } catch (Exception e) {
            log.warn("매칭 알림 재발송 실패 - standbyId={}, userId={}", standby.getStandbyId(), standby.getUserId(), e);
            return false;
        }
    }
}
