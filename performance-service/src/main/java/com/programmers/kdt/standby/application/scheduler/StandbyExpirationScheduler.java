package com.programmers.kdt.standby.application.scheduler;

import com.programmers.kdt.standby.application.service.StandbyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class StandbyExpirationScheduler {

    private final StandbyService standbyService;

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "standbyExpiration", lockAtMostFor = "2m", lockAtLeastFor = "25s")
    public void expireHeldStandbys() {
        int expiredCount = standbyService.expireHeldStandbys();

        if (expiredCount > 0) {
            log.info("결제 미완료로 만료된 대기 {} 건 처리", expiredCount);
        }
    }
}
