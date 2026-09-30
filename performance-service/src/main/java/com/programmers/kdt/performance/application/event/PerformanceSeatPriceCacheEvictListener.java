package com.programmers.kdt.performance.application.event;

import com.programmers.kdt.performance.domain.event.PerformanceSeatPriceCacheEvictEvent;

import com.programmers.kdt.performance.infrastructure.cache.PerformanceSeatPriceCacheStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PerformanceSeatPriceCacheEvictListener {

    private final PerformanceSeatPriceCacheStore performanceSeatPriceCacheStore;

    // 삭제 트랜잭션 커밋 전에 캐시를 지우면, 커밋 사이에 끼어든 조회가 삭제 대상 데이터로 캐시를 다시 채울 수 있어 AFTER_COMMIT에서 처리한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evictCacheAfterCommit(PerformanceSeatPriceCacheEvictEvent event) {
        event.zones().forEach(zone -> performanceSeatPriceCacheStore.evict(event.performanceId(), zone));
    }
}