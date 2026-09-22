package com.programmers.kdt.payment.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.SetCondition;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyKeyServiceImpl implements IdempotencyKeyService {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private record IdempotencyRecord(String requestHash, String responseBody) {
    }

    @Override
    public Optional<String> generate(String idempotencyKey, String requestHash) {
        String freshValue = toJson(new IdempotencyRecord(requestHash, null));
        Boolean created = redisTemplate.opsForValue().setIfAbsent(idempotencyKey, freshValue, TTL);
        if (Boolean.TRUE.equals(created)) {
            return Optional.empty();
        }

        String existingJson = redisTemplate.opsForValue().get(idempotencyKey);
        if (existingJson == null) {
            // setIfAbsent 실패 직후 TTL 만료로 사라진 찰나의 경합 - 클라이언트가 재시도하도록 유도
            throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_EXISTS);
        }

        IdempotencyRecord existing = fromJson(existingJson);
        if (!existing.requestHash().equals(requestHash)) {
            throw new BusinessException(PaymentErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        if (existing.responseBody() == null) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_EXISTS);
        }
        return Optional.of(existing.responseBody());
    }

    @Override
    public void complete(String idempotencyKey, String responseBody) {
        String existingJson = redisTemplate.opsForValue().get(idempotencyKey);
        if (existingJson == null) {
            log.info("[IDEMPOTENCY_COMPLETE] key={}, found=0, bodyLen={}", idempotencyKey, responseBody == null ? -1 : responseBody.length());
            return;
        }

        IdempotencyRecord existing = fromJson(existingJson);
        String completedValue = toJson(new IdempotencyRecord(existing.requestHash(), responseBody));
        setKeepingTtl(idempotencyKey, completedValue);
        log.info("[IDEMPOTENCY_COMPLETE] key={}, found=1, bodyLen={}", idempotencyKey, responseBody == null ? -1 : responseBody.length());
    }

    @Override
    public void release(String idempotencyKey) {
        redisTemplate.delete(idempotencyKey);
    }

    // TTL 그대로 유지한 채 값만 덮어쓰기 (complete() 시 5분 카운트가 재시작되면 안 됨)
    private void setKeepingTtl(String key, String value) {
        redisTemplate.execute((RedisCallback<Boolean>) connection -> connection.stringCommands().set(
                key.getBytes(StandardCharsets.UTF_8),
                value.getBytes(StandardCharsets.UTF_8),
                SetCondition.upsert(),
                Expiration.keepTtl()
        ));
    }

    private String toJson(IdempotencyRecord record) {
        return objectMapper.writeValueAsString(record);
    }

    private IdempotencyRecord fromJson(String json) {
        return objectMapper.readValue(json, IdempotencyRecord.class);
    }
}
