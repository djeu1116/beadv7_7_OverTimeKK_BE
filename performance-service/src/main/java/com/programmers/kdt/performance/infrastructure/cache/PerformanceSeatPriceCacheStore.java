package com.programmers.kdt.performance.infrastructure.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 공연/구역별 좌석 가격을 캐싱한다. 등록 이후 수정 경로가 없는 사실상 불변값이라
 * key: performance:seat-price:{performanceId}:{zone} 하나의 String 값(price)으로 저장한다.
 */
@Component
public class PerformanceSeatPriceCacheStore {

    private static final String KEY_PREFIX = "performance:seat-price:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public PerformanceSeatPriceCacheStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<Long> find(Long performanceId, String zone) {
        String value = redisTemplate.opsForValue().get(key(performanceId, zone));
        return Optional.ofNullable(value).map(Long::valueOf);
    }

    public void save(Long performanceId, String zone, Long price) {
        redisTemplate.opsForValue().set(key(performanceId, zone), String.valueOf(price), TTL);
    }

    public void evict(Long performanceId, String zone) {
        redisTemplate.delete(key(performanceId, zone));
    }

    private String key(Long performanceId, String zone) {
        return KEY_PREFIX + performanceId + ":" + zone;
    }
}