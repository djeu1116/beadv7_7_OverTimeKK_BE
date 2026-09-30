package com.programmers.kdt.user.infrastructure.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 사용자당 유효한 refresh token(jti)을 1개만 Redis에 보관한다.
 * 기기당 1세션만 허용 - 새로 저장하면 이전 세션은 자동으로 무효화된다.
 */
@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:user:";

    /**
     * 저장된 값이 ARGV[1](기대 tokenId)과 같을 때만 ARGV[2](새 tokenId)로 교체한다.
     * GET-비교-SET을 하나의 원자적 연산으로 묶어, 동시에 들어온 두 refresh 요청이
     * 서로 다른 새 토큰으로 순차 덮어쓰기 하는 레이스를 막는다.
     */
    private static final RedisScript<Long> COMPARE_AND_ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then
                return 0
            end
            if current ~= ARGV[1] then
                redis.call('DEL', KEYS[1])
                return -1
            end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RefreshTokenStore(StringRedisTemplate redisTemplate,
                              @Value("${jwt.refresh-expiration-millis}") long refreshExpirationMillis) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMillis(refreshExpirationMillis);
    }

    public void save(Long userId, String tokenId) {
        redisTemplate.opsForValue().set(key(userId), tokenId, ttl);
    }

    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    /**
     * 저장된 tokenId가 expectedTokenId와 일치할 때만 newTokenId로 원자적으로 교체한다(compare-and-set).
     * 불일치하면 재사용 시도로 간주해 즉시 삭제한다.
     */
    public RotateResult compareAndRotate(Long userId, String expectedTokenId, String newTokenId) {
        Long result = redisTemplate.execute(COMPARE_AND_ROTATE_SCRIPT,
                List.of(key(userId)), expectedTokenId, newTokenId, String.valueOf(ttl.getSeconds()));

        if (result == null || result == 0L) {
            return RotateResult.NOT_FOUND;
        }
        return result == 1L ? RotateResult.ROTATED : RotateResult.REUSE_DETECTED;
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    public enum RotateResult {
        ROTATED, NOT_FOUND, REUSE_DETECTED
    }
}
