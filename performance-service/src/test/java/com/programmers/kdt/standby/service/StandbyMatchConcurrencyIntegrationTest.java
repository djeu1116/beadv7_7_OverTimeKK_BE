package com.programmers.kdt.standby.service;

import com.programmers.kdt.performance.entity.Performance;
import com.programmers.kdt.performance.entity.PerformanceSession;
import com.programmers.kdt.standby.entity.Standby;
import com.programmers.kdt.standby.entity.StandbyStatus;
import com.programmers.kdt.standby.event.StandbyTicketEvent;
import com.programmers.kdt.standby.repository.StandbyRepository;
import com.programmers.kdt.ticket.event.StandbyCheckRequestEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.springframework.dao.CannotAcquireLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// 매칭이 동시에 몰려도 FIFO 순서가 깨지거나 다른 zone 대기자가 잘못 매칭되지 않는지,
// PESSIMISTIC_WRITE 락으로 실제 동시성 하에서 정합성이 보장되는지 실제 MySQL로 검증.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=60",
        "spring.datasource.hikari.minimum-idle=10"
})
@Import({StandbyService.class, StandbyMatchConcurrencyIntegrationTest.MatchEventCollector.class})
class StandbyMatchConcurrencyIntegrationTest {

    private static final int ZONE_A_CANDIDATES = 200;
    private static final int ZONE_B_NOISE_CANDIDATES = 100;
    private static final int CONCURRENT_RELEASES = 60;
    private static final int THREAD_POOL_SIZE = 15;

    @Autowired
    private StandbyService standbyService;
    @Autowired
    private StandbyRepository standbyRepository;
    @Autowired
    private EntityManager em;
    @Autowired
    private MatchEventCollector matchEventCollector;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private PerformanceSession session;
    private Long performanceId;

    @TestConfiguration
    static class MatchEventCollector {
        private final List<StandbyTicketEvent> events = Collections.synchronizedList(new ArrayList<>());

        @EventListener
        void onMatch(StandbyTicketEvent event) {
            events.add(event);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동시에 여러 티켓이 취소/만료돼도 FIFO 순서를 어기거나 다른 zone 대기자를 잘못 매칭하지 않는다.")
    void concurrentMatchingPreservesFifoAndZoneCorrectness() {
        // given - zone A에 신청 순서를 아는 대기자 200명, zone B에 노이즈 대기자 100명
        // (테스트 메서드 자체는 NOT_SUPPORTED라 트랜잭션이 없음 - 세팅은 별도 트랜잭션으로 커밋해서
        //  뒤의 동시 스레드들이 각자 커넥션으로 이 데이터를 볼 수 있게 함)
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        List<List<Long>> seeded = txTemplate.execute(status -> {
            setUpSession();
            List<Long> a = seedStandbyCandidates("A", ZONE_A_CANDIDATES, 1000L, LocalDateTime.now().minusMinutes(10));
            List<Long> b = seedStandbyCandidates("B", ZONE_B_NOISE_CANDIDATES, 5000L, LocalDateTime.now().minusMinutes(10));
            return List.of(a, b);
        });
        List<Long> zoneAIdsInOrder = seeded.get(0);
        List<Long> zoneBIdsInOrder = seeded.get(1);

        // when - zone A 티켓 100장이 "동시에" 취소/만료됐다고 가정하고 매칭 요청 100건을 동시에 쏨
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicLong ticketIdSeq = new AtomicLong(1);
        AtomicLong deadlockRetries = new AtomicLong(0);

        List<CompletableFuture<Long>> futures = IntStream.range(0, CONCURRENT_RELEASES)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    long ticketId = ticketIdSeq.getAndIncrement();
                    try {
                        startGate.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    long start = System.nanoTime();
                    // InnoDB PESSIMISTIC_WRITE + ORDER BY 조합은 동시 경합이 심하면 데드락으로 감지될 수 있음
                    // (실제로 관찰됨) - DB 트랜잭션 재시도는 흔한 패턴이라 테스트에서도 짧게 재시도.
                    for (int attempt = 1; ; attempt++) {
                        try {
                            standbyService.StandbyCheck(new StandbyCheckRequestEvent(performanceId, 1L, "A", ticketId));
                            break;
                        } catch (CannotAcquireLockException e) {
                            deadlockRetries.incrementAndGet();
                            if (attempt >= 100) throw e;
                            try {
                                Thread.sleep((long) (Math.random() * 30));
                            } catch (InterruptedException ie) {
                                throw new RuntimeException(ie);
                            }
                        }
                    }
                    return System.nanoTime() - start;
                }, pool))
                .collect(Collectors.toList());

        long wallStart = System.nanoTime();
        startGate.countDown();
        List<Long> latenciesNs = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        long wallElapsedMs = (System.nanoTime() - wallStart) / 1_000_000;
        pool.shutdown();

        // then
        Set<Long> heldZoneAIds = standbyRepository.findAllById(zoneAIdsInOrder).stream()
                .filter(s -> s.getStandbyStatus() == StandbyStatus.HELD)
                .map(Standby::getStandbyId)
                .collect(Collectors.toSet());

        Set<Long> expectedFifoWinners = zoneAIdsInOrder.stream()
                .limit(CONCURRENT_RELEASES)
                .collect(Collectors.toSet());

        long fifoViolations = heldZoneAIds.stream().filter(id -> !expectedFifoWinners.contains(id)).count()
                + expectedFifoWinners.stream().filter(id -> !heldZoneAIds.contains(id)).count();

        long zoneMismatches = standbyRepository.findAllById(zoneBIdsInOrder).stream()
                .filter(s -> s.getStandbyStatus() == StandbyStatus.HELD)
                .count();

        System.out.println("=== 대기열 동시성 매칭 검증 (zone A 후보 " + ZONE_A_CANDIDATES + "명, 동시 매칭요청 "
                + CONCURRENT_RELEASES + "건, 스레드풀 " + THREAD_POOL_SIZE + ") ===");
        System.out.println("FIFO_VIOLATIONS=" + fifoViolations);
        System.out.println("ZONE_MISMATCH=" + zoneMismatches);
        System.out.println("DEADLOCK_RETRIES=" + deadlockRetries.get());
        System.out.println("실제 HELD 건수=" + heldZoneAIds.size() + " (기대값=" + CONCURRENT_RELEASES + ")");
        System.out.println("발행된 매칭 이벤트 수=" + matchEventCollector.events.size());
        System.out.println("전체 소요시간(wall)=" + wallElapsedMs + "ms");
        System.out.println("건당 지연 p50/p95/max(ms)=" + percentileMs(latenciesNs, 50) + "/"
                + percentileMs(latenciesNs, 95) + "/" + (latenciesNs.stream().mapToLong(Long::longValue).max().orElse(0) / 1_000_000));

        assertThat(fifoViolations).as("FIFO 순서 위반 건수").isZero();
        assertThat(zoneMismatches).as("다른 zone 대기자가 잘못 매칭된 건수").isZero();
        assertThat(heldZoneAIds).hasSize(CONCURRENT_RELEASES);
    }

    private double percentileMs(List<Long> latenciesNs, int p) {
        List<Long> sorted = latenciesNs.stream().sorted().collect(Collectors.toList());
        int idx = Math.max(0, Math.min(sorted.size() - 1, (int) Math.ceil(p / 100.0 * sorted.size()) - 1));
        return sorted.get(idx) / 1_000_000.0;
    }

    private void setUpSession() {
        Performance performance = Performance.createPerformance(
                "동시성 테스트 공연", "설명", 120L,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(11), null,
                1L, 1L, null);
        em.persist(performance);
        em.flush();
        performanceId = performance.getPerformanceId();

        session = PerformanceSession.createInitial(1L, performance, "배우", LocalDateTime.now().plusDays(10));
        em.persist(session);
        em.flush();
    }

    private List<Long> seedStandbyCandidates(String zone, int count, long userIdOffset, LocalDateTime baseTime) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Standby standby = Standby.apply(userIdOffset + i, session, List.of(zone));
            em.persist(standby);
            em.flush();
            LocalDateTime reservedAt = baseTime.plusNanos(i * 1_000_000L); // 1ms 간격으로 신청 순서 고정
            em.createQuery("update Standby s set s.reservedAt = :t where s.standbyId = :id")
                    .setParameter("t", reservedAt)
                    .setParameter("id", standby.getStandbyId())
                    .executeUpdate();
            ids.add(standby.getStandbyId());
        }
        return ids;
    }

    // 이 테스트가 만든 performanceId 범위만 정리 - 로컬 DB의 다른 실제 데이터를 건드리면 안 됨.
    @AfterEach
    void cleanUp() {
        if (performanceId == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            em.createQuery("delete from Standby s where s.performanceSession.performanceSessionId.performanceId = :pid")
                    .setParameter("pid", performanceId)
                    .executeUpdate();
            em.createQuery("delete from PerformanceSession p where p.performanceSessionId.performanceId = :pid")
                    .setParameter("pid", performanceId)
                    .executeUpdate();
            em.createQuery("delete from Performance p where p.performanceId = :pid")
                    .setParameter("pid", performanceId)
                    .executeUpdate();
        });
    }
}
