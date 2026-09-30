package com.programmers.kdt.ticket.infrastructure.cache;

import com.programmers.kdt.ticket.presentation.dto.SessionZoneKey;
import com.programmers.kdt.ticket.presentation.dto.TicketZoneResponse;
import com.programmers.kdt.ticket.domain.entity.Ticket;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * /api/tickets/select/seat 조회 결과(zone 단위 좌석 목록)를 Redis Hash로 캐싱한다.
 * key: ticket:zone:{performanceId}:{sessionNum}:{zone}, field: ticketId, value: "seatRow:seatNum:status"(0=선택가능,1=선택불가)
 * hold 최초 점유 / available 복귀 시점에만 필드를 갱신한다. 그 외 전이(reserve 등)는 이미 1로 표시된 상태끼리의
 * 전이라 캐시를 건드릴 필요가 없고, TTL이 지나면 DB 기준으로 통째로 재구성된다.
 */
@Component
public class TicketZoneCacheStore {

    private static final String KEY_PREFIX = "ticket:zone:";
    private static final String VALUE_DELIMITER = ":";
    private static final Duration TTL = Duration.ofHours(24);
    private static final int SELECTABLE = 0;
    private static final int NOT_SELECTABLE = 1;

    private final StringRedisTemplate redisTemplate;

    public TicketZoneCacheStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<List<TicketZoneResponse>> find(SessionZoneKey key) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(key));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entries.entrySet().stream()
                .map(entry -> toResponse((String) entry.getKey(), (String) entry.getValue()))
                .toList());
    }

    public void save(SessionZoneKey key, List<TicketZoneResponse> zones) {
        if (zones.isEmpty()) {
            return;
        }
        String redisKey = key(key);
        Map<String, String> fields = zones.stream()
                .collect(Collectors.toMap(zone -> String.valueOf(zone.ticketId()), this::toValue));
        redisTemplate.opsForHash().putAll(redisKey, fields);
        redisTemplate.expire(redisKey, TTL);
    }

    public void markHold(Ticket ticket) {
        updateIfCached(ticket, NOT_SELECTABLE);
    }

    public void markAvailable(Ticket ticket) {
        updateIfCached(ticket, SELECTABLE);
    }

    // 캐시가 아직 없는(cache miss) zone에 필드 하나만 HSET 해버리면 좌석 일부만 있는
    // 불완전한 캐시가 생겨 이후 HGETALL을 캐시 히트로 오인하게 된다. 이미 채워진 zone만 갱신한다.
    private void updateIfCached(Ticket ticket, int status) {
        String redisKey = key(new SessionZoneKey(ticket.getPerformanceId(), ticket.getSessionNum(), ticket.getZone()));
        if (Boolean.FALSE.equals(redisTemplate.hasKey(redisKey))) {
            return;
        }
        String value = ticket.getSeatRow() + VALUE_DELIMITER + ticket.getSeatNum() + VALUE_DELIMITER + status;
        redisTemplate.opsForHash().put(redisKey, String.valueOf(ticket.getTicketId()), value);
    }

    private String key(SessionZoneKey key) {
        return KEY_PREFIX + key.performanceId() + VALUE_DELIMITER + key.sessionNum() + VALUE_DELIMITER + key.zone();
    }

    private String toValue(TicketZoneResponse response) {
        return response.seatRow() + VALUE_DELIMITER + response.seatNum() + VALUE_DELIMITER + response.ticketStatus();
    }

    private TicketZoneResponse toResponse(String ticketId, String value) {
        String[] parts = value.split(VALUE_DELIMITER, 3);
        return new TicketZoneResponse(Long.valueOf(ticketId), parts[0], parts[1], Integer.parseInt(parts[2]));
    }
}