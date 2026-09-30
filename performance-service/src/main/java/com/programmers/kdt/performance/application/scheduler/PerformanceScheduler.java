package com.programmers.kdt.performance.application.scheduler;

import com.programmers.kdt.performance.application.service.PerformanceV2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PerformanceScheduler {

    private final PerformanceV2Service performanceService;

    @Scheduled(cron = "0 1 0 * * *")
    public void closedPerformance() {
        int updateCount = performanceService.closedPerformance();

        log.info("종료된 공연 {} 건", updateCount);
    }
}
