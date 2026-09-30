package com.programmers.kdt.payment.application.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.domain.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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

    // Redis는 "빠른 경로"(재시도 시 캐시된 응답 반환)일 뿐, 최종 정합성은 DB 제약(주문당 결제 1행 unique,
    // Payment 상태 전이 가드, 낙관락)이 이미 지키고 있다. 그래서 Redis 장애 시 결제 자체를 막지 않고
    // (fail-closed) 캐시 없이 그대로 진행시킨다(degrade) - 대신 재시도 요청이 캐시된 응답 대신 DB 가드에
    // 걸려 에러를 받을 수 있음(UX 저하), 이건 감수한다. 열린 질문으로 남아있던 "fail-closed vs DB degrade"
    // 결정: DB degrade.
    @Override
    public Optional<String> generate(String idempotencyKey, String requestHash) {
        try {
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
        } catch (DataAccessException e) {
            log.warn("[IDEMPOTENCY_REDIS_DEGRADED] Redis 장애로 멱등성 캐시 없이 진행 - key={}", idempotencyKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void complete(String idempotencyKey, String responseBody) {
        try {
            String existingJson = redisTemplate.opsForValue().get(idempotencyKey);
            if (existingJson == null) {
                log.info("[IDEMPOTENCY_COMPLETE] key={}, found=0, bodyLen={}", idempotencyKey, responseBody == null ? -1 : responseBody.length());
                return;
            }

            IdempotencyRecord existing = fromJson(existingJson);
            String completedValue = toJson(new IdempotencyRecord(existing.requestHash(), responseBody));
            setKeepingTtl(idempotencyKey, completedValue);
            log.info("[IDEMPOTENCY_COMPLETE] key={}, found=1, bodyLen={}", idempotencyKey, responseBody == null ? -1 : responseBody.length());
        } catch (DataAccessException e) {
            // 캐시를 못 남겨도 다음 재시도가 어차피 같은 방식으로 degrade되어 DB 가드로 안전하게 처리됨
            log.warn("[IDEMPOTENCY_REDIS_DEGRADED] Redis 장애로 응답 캐시를 못 남김 - key={}", idempotencyKey, e);
        }
    }

    @Override
    public void release(String idempotencyKey) {
        try {
            redisTemplate.delete(idempotencyKey);
        } catch (DataAccessException e) {
            // 못 지워도 TTL(5분)이 지나면 자연 소멸 - 안전
            log.warn("[IDEMPOTENCY_REDIS_DEGRADED] Redis 장애로 키 삭제를 못 함 - key={}", idempotencyKey, e);
        }
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
