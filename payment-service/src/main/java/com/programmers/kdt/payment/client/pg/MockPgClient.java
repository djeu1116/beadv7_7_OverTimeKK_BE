package com.programmers.kdt.payment.client.pg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

// 실제 PG사가 아닌 모킹으로 우선 구현(토스페이로 교체 예정)
@Profile("!toss")
@Component
public class MockPgClient implements PgClient {

    private static final Logger log = LoggerFactory.getLogger(MockPgClient.class);

    private final Map<String, Queue<Supplier<PgApproveResult>>> approveBehaviors = new ConcurrentHashMap<>();
    private final Map<String, Queue<Supplier<PgApproveResult>>> selectBehaviors = new ConcurrentHashMap<>();
    // 부하테스트에서 같은 pgOrderId로 approve()가 두 번 이상 나가는지(PG 이중승인) 직접 확인하기 위한 카운터.
    // transactionKey(payment.paymentKey)는 테스트 스크립트가 전부 같은 문자열을 재사용해서 키로 못 씀 —
    // pgOrderId는 pay() 시점에 payment마다 한 번만 생성되고 이후 안 바뀌므로 payment 단위 카운팅에 씀.
    private final Map<String, AtomicLong> approveCallCounts = new ConcurrentHashMap<>();

    @Value("${pg.mock.approve-delay-ms:0}")
    private long approveDelayMs;

    public void stubApprove(String paymentKey, Supplier<PgApproveResult> behavior) {
        approveBehaviors.computeIfAbsent(paymentKey, k -> new ConcurrentLinkedQueue<>()).add(behavior);
    }

    public void stubSelect(String paymentKey, Supplier<PgApproveResult> behavior) {
        selectBehaviors.computeIfAbsent(paymentKey, k -> new ConcurrentLinkedQueue<>()).add(behavior);
    }

    public void reset() {
        approveBehaviors.clear();
        selectBehaviors.clear();
        approveCallCounts.clear();
    }

    @Override
    public PgReadyResult ready(PgReadyCommand command) {
        String transactionKey = "Mock-" + UUID.randomUUID();
        String orderId = PgOrderIdFormatter.format(command.orderId());
        return new PgReadyResult(transactionKey, orderId, "https://mock-pg.local/pay/" + transactionKey);
    }

    @Override
    public PgApproveResult approve(PgApproveCommand command) {
        long count = approveCallCounts.computeIfAbsent(command.orderId(), k -> new AtomicLong()).incrementAndGet();
        log.info("PG approve 호출 - pgOrderId={} count={}", command.orderId(), count);
        sleepIfConfigured();
        return resolve(approveBehaviors, command.transactionKey());
    }

    @Override
    public PgCancelResult cancel(PgCancelCommand command) {
        return new PgCancelResult(true, LocalDateTime.now());
    }

    @Override
    public PgApproveResult select(String paymentKey) {
        return resolve(selectBehaviors, paymentKey);
    }

    private void sleepIfConfigured() {
        if (approveDelayMs <= 0) return;
        try {
            Thread.sleep(approveDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private PgApproveResult resolve(Map<String, Queue<Supplier<PgApproveResult>>> behaviors, String key) {
        Queue<Supplier<PgApproveResult>> queue = behaviors.get(key);
        if (queue == null || queue.isEmpty()) {
            return new PgApproveResult(true, LocalDateTime.now());
        }
        Supplier<PgApproveResult> behavior = queue.size() > 1 ? queue.poll() : queue.peek();
        return behavior.get();
    }
}
