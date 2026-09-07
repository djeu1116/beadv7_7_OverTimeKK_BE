package com.programmers.kdt.payment.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyKeyServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private IdempotencyKeyServiceImpl idempotencyKeyService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        idempotencyKeyService = new IdempotencyKeyServiceImpl(redisTemplate, objectMapper);
    }

    private String recordJson(String requestHash, String responseBody) {
        return objectMapper.writeValueAsString(new IdempotencyRecordMirror(requestHash, responseBody));
    }

    // IdempotencyKeyServiceImpl.IdempotencyRecord와 동일한 필드 모양의 테스트용 미러 (private record라 직접 못 씀)
    private record IdempotencyRecordMirror(String requestHash, String responseBody) {
    }

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("최초 요청이면 새로 저장하고 빈 값을 반환한다.")
        void freshKey_insertsAndReturnEmpty() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("key-1"), anyString(), eq(Duration.ofMinutes(5)))).thenReturn(true);

            Optional<String> result = idempotencyKeyService.generate("key-1", "hash-1");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("같은 키+같은 요청이 이미 완료됐으면 캐시된 응답을 그대로 반환한다.")
        void sameKeyHash_completed_returnsCachedResponse() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("key-1"), anyString(), any(Duration.class))).thenReturn(false);
            when(valueOperations.get("key-1")).thenReturn(recordJson("hash-1", "{\"paymentId\":1}"));

            Optional<String> result = idempotencyKeyService.generate("key-1", "hash-1");

            assertThat(result).contains("{\"paymentId\":1}");
        }

        @Test
        @DisplayName("같은 키 + 같은 요청이 아직 처리중이면 PAYMENT_ALREADY_EXISTS 예외가 발생한다.")
        void sameKeySameHash_stillPending_throwsAlreadyExists() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("key-1"), anyString(), any(Duration.class))).thenReturn(false);
            when(valueOperations.get("key-1")).thenReturn(recordJson("hash-1", null));

            assertThatThrownBy(() -> idempotencyKeyService.generate("key-1", "hash-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("같은 키인데 요청 내용(hash)이 다르면 IDEMPOTENCY_KEY_CONFLICT 예외가 발생한다.")
        void sameKeyDifferentHash_throwsConflict() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("key-1"), anyString(), any(Duration.class))).thenReturn(false);
            when(valueOperations.get("key-1")).thenReturn(recordJson("hash-1", null));

            assertThatThrownBy(() -> idempotencyKeyService.generate("key-1", "hash-2"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        @Test
        @DisplayName("같은 키+다른 hash면, 기존 요청이 처리중이어도 CONFLICT가 ALREADY_EXISTS보다 우선한다.")
        void sameKeyDifferentHash_evenIfPending_conflictWinsOverAlreadyExists() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("key-1"), anyString(), any(Duration.class))).thenReturn(false);
            when(valueOperations.get("key-1")).thenReturn(recordJson("hash-1", "{\"paymentId\":1}"));

            assertThatThrownBy(() -> idempotencyKeyService.generate("key-1", "hash-2"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        @Test
        @DisplayName("setIfAbsent 실패 후 재조회에서도 못 찾으면(TTL 만료 경합) ALREADY_EXISTS로 재시도를 유도한다.")
        void raceLoser_getReturnsNull_throwsAlreadyExists() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("key-1"), anyString(), any(Duration.class))).thenReturn(false);
            when(valueOperations.get("key-1")).thenReturn(null);

            assertThatThrownBy(() -> idempotencyKeyService.generate("key-1", "hash-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("complete")
    class Complete {

        @Test
        @DisplayName("존재하는 키의 응답 본문을 완료 처리한다 (TTL 유지).")
        void completeExistingKey() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("key-1")).thenReturn(recordJson("hash-1", null));
            RedisConnection connection = mock(RedisConnection.class);
            RedisStringCommands stringCommands = mock(RedisStringCommands.class);
            when(redisTemplate.execute(any(org.springframework.data.redis.core.RedisCallback.class)))
                    .thenAnswer(invocation -> {
                        org.springframework.data.redis.core.RedisCallback<?> callback = invocation.getArgument(0);
                        when(connection.stringCommands()).thenReturn(stringCommands);
                        return callback.doInRedis(connection);
                    });

            idempotencyKeyService.complete("key-1", "{\"result\":true}");

            verify(stringCommands).set(any(byte[].class), any(byte[].class), any(org.springframework.data.redis.connection.SetCondition.class), any());
        }

        @Test
        @DisplayName("존재하지 않는 키면 예외 없이 조용히 무시한다.")
        void missingKey_noOp() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("key-1")).thenReturn(null);

            assertThatCode(() -> idempotencyKeyService.complete("key-1", "{}"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("release")
    class Release {
        @Test
        @DisplayName("키를 삭제한다.")
        void deletesKey() {
            idempotencyKeyService.release("key-1");

            verify(redisTemplate).delete("key-1");
        }
    }
}
