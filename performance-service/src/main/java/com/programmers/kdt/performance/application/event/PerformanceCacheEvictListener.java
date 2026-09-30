package com.programmers.kdt.performance.application.event;

import com.programmers.kdt.performance.domain.event.PerformanceCacheEvictEvent;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PerformanceCacheEvictListener {
    private final CacheManager manager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evictCacheAfterCommit(PerformanceCacheEvictEvent event) {
        Cache cache = manager.getCache(event.value());
        if (cache == null) {
            return;
        }

        if ("all".equals(event.key())) {
            cache.clear();
        } else {
            cache.evict(event.key());
        }
    }
}
