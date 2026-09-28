package com.programmers.kdt.payment.scheduler;

import com.programmers.kdt.common.reconciliation.ReconciliationTaskType;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskWriter;
import com.programmers.kdt.payment.entity.PointLedger;
import com.programmers.kdt.payment.entity.PointLog;
import com.programmers.kdt.payment.entity.PointType;
import com.programmers.kdt.payment.repository.PointLedgerRepository;
import com.programmers.kdt.payment.repository.PointLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointLedgerInvariantSchedulerTest {

    @Mock
    private PointLedgerRepository pointLedgerRepository;
    @Mock
    private PointLogRepository pointLogRepository;
    @Mock
    private ReconciliationTaskWriter reconciliationTaskWriter;

    private PointLedgerInvariantScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PointLedgerInvariantScheduler(pointLedgerRepository, pointLogRepository, reconciliationTaskWriter);
        // 기본값: 원장/사용로그 둘 다 빈 페이지 - 개별 테스트가 필요한 쪽만 재스텁
        lenient().when(pointLedgerRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        lenient().when(pointLogRepository.findByPointType(eq(PointType.USE), any()))
                .thenReturn(new PageImpl<>(List.of()));
    }

    private PointLedger ledger(Long id, Long useLogId, Long usedAmount, Long remainingRefundable) {
        PointLedger ledger = PointLedger.create(1L, useLogId, usedAmount);
        ledger.consume(usedAmount - remainingRefundable); // remainingRefundable을 원하는 값으로 맞춤
        ReflectionTestUtils.setField(ledger, "id", id);
        return ledger;
    }

    private PointLog useLog(Long id, Long userId, Long amount) {
        PointLog log = PointLog.use(userId, amount, "event-" + id);
        ReflectionTestUtils.setField(log, "id", id);
        return log;
    }

    @Test
    @DisplayName("원장도 사용로그도 없으면 아무 것도 기록하지 않는다.")
    void nothingToCheck_recordsNothing() {
        scheduler.verifyInvariants();

        verifyNoInteractions(reconciliationTaskWriter);
    }

    @Test
    @DisplayName("원장이 암시하는 환급액과 로그의 실제 환급 합계가 같으면 정상 - 기록 없음.")
    void consistentLedger_noMismatch() {
        PointLedger ledger = ledger(1L, 100L, 3000L, 1000L); // 2000원 환급된 것으로 되어 있음
        when(pointLedgerRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ledger)));
        when(pointLogRepository.sumAmountByRefLogIdAndPointTypeIn(eq(100L), anyList())).thenReturn(2000L);

        scheduler.verifyInvariants();

        verifyNoInteractions(reconciliationTaskWriter);
    }

    @Test
    @DisplayName("원장이 암시하는 환급액과 로그의 실제 환급 합계가 다르면 불일치로 기록한다.")
    void inconsistentLedger_recordsMismatch() {
        PointLedger ledger = ledger(2L, 200L, 3000L, 1000L); // 원장은 2000원 환급됐다고 하는데
        when(pointLedgerRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ledger)));
        when(pointLogRepository.sumAmountByRefLogIdAndPointTypeIn(eq(200L), anyList())).thenReturn(500L); // 로그엔 500원뿐

        scheduler.verifyInvariants();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.POINT_LEDGER_INVARIANT_MISMATCH), eq(2L), anyString());
    }

    @Test
    @DisplayName("사용 로그에 대응하는 원장 행이 있으면 정상 - 기록 없음.")
    void useLogWithLedger_noMismatch() {
        PointLog log = useLog(10L, 1L, 3000L);
        when(pointLogRepository.findByPointType(eq(PointType.USE), any()))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(pointLedgerRepository.findByUseLogId(10L)).thenReturn(Optional.of(ledger(1L, 10L, 3000L, 3000L)));

        scheduler.verifyInvariants();

        verifyNoInteractions(reconciliationTaskWriter);
    }

    @Test
    @DisplayName("사용 로그에 대응하는 원장 행이 없으면 원장 누락으로 기록한다.")
    void useLogWithoutLedger_recordsMismatch() {
        PointLog log = useLog(11L, 1L, 3000L);
        when(pointLogRepository.findByPointType(eq(PointType.USE), any()))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(pointLedgerRepository.findByUseLogId(11L)).thenReturn(Optional.empty());

        scheduler.verifyInvariants();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.POINT_LEDGER_INVARIANT_MISMATCH), eq(11L), anyString());
    }

    @Test
    @DisplayName("한 건에서 예상치 못한 예외가 나도 나머지 원장은 계속 처리된다.")
    void unexpectedExceptionOnOneLedger_doesNotStopBatch() {
        PointLedger broken = ledger(3L, 300L, 1000L, 1000L);
        PointLedger healthy = ledger(4L, 400L, 1000L, 0L);
        when(pointLedgerRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(broken, healthy)));
        when(pointLogRepository.sumAmountByRefLogIdAndPointTypeIn(eq(300L), anyList()))
                .thenThrow(new RuntimeException("예상치 못한 오류"));
        when(pointLogRepository.sumAmountByRefLogIdAndPointTypeIn(eq(400L), anyList())).thenReturn(500L); // 기대값 1000, 실제 500 -> 불일치

        scheduler.verifyInvariants();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.POINT_LEDGER_INVARIANT_MISMATCH), eq(4L), anyString());
        verify(reconciliationTaskWriter, never())
                .record(eq(ReconciliationTaskType.POINT_LEDGER_INVARIANT_MISMATCH), eq(3L), anyString());
    }

    @Test
    @DisplayName("배치 자체가 실패(레포지토리 예외)하면 aggregateId 0으로 배치 실패를 기록한다.")
    void batchFails_recordsBatchFailure() {
        when(pointLedgerRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenThrow(new RuntimeException("DB 연결 실패"));

        scheduler.verifyInvariants();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.POINT_LEDGER_INVARIANT_MISMATCH), eq(0L), anyString());
    }

    @Test
    @DisplayName("한 페이지를 넘는 원장은 다음 페이지까지 이어서 처리한다.")
    void multiplePages_processesAll() {
        PointLedger first = ledger(5L, 500L, 1000L, 0L);
        PointLedger second = ledger(6L, 600L, 1000L, 0L);
        Page<PointLedger> page0 = new PageImpl<>(List.of(first), PageRequest.of(0, 1), 2);
        Page<PointLedger> page1 = new PageImpl<>(List.of(second), PageRequest.of(1, 1), 2);
        when(pointLedgerRepository.findAll(argThat((org.springframework.data.domain.Pageable p) -> p != null && p.getPageNumber() == 0)))
                .thenReturn(page0);
        when(pointLedgerRepository.findAll(argThat((org.springframework.data.domain.Pageable p) -> p != null && p.getPageNumber() == 1)))
                .thenReturn(page1);
        when(pointLogRepository.sumAmountByRefLogIdAndPointTypeIn(eq(500L), anyList())).thenReturn(500L); // 기대 1000 vs 실제 500 -> 불일치
        when(pointLogRepository.sumAmountByRefLogIdAndPointTypeIn(eq(600L), anyList())).thenReturn(600L); // 기대 1000 vs 실제 600 -> 불일치

        scheduler.verifyInvariants();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.POINT_LEDGER_INVARIANT_MISMATCH), eq(5L), anyString());
        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.POINT_LEDGER_INVARIANT_MISMATCH), eq(6L), anyString());
    }
}
