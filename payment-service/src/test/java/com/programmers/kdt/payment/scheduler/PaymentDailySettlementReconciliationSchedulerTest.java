package com.programmers.kdt.payment.scheduler;

import com.programmers.kdt.common.reconciliation.ReconciliationTaskType;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskWriter;
import com.programmers.kdt.payment.client.pg.PgApproveResult;
import com.programmers.kdt.payment.client.pg.PgClient;
import com.programmers.kdt.payment.client.pg.PgClientException;
import com.programmers.kdt.payment.entity.Payment;
import com.programmers.kdt.payment.entity.PaymentStatus;
import com.programmers.kdt.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentDailySettlementReconciliationSchedulerTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PgClient pgClient;
    @Mock
    private ReconciliationTaskWriter reconciliationTaskWriter;

    private PaymentDailySettlementReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PaymentDailySettlementReconciliationScheduler(paymentRepository, pgClient, reconciliationTaskWriter);
    }

    private Payment payment(Long id, PaymentStatus status, String paymentKey) {
        Payment payment = Payment.create(1L, 10L, 10000L);
        ReflectionTestUtils.setField(payment, "id", id);
        ReflectionTestUtils.setField(payment, "paymentStatus", status);
        ReflectionTestUtils.setField(payment, "paymentKey", paymentKey);
        return payment;
    }

    private void stubTarget(Payment... payments) {
        when(paymentRepository.findByPaymentStatusInAndModifiedAtAfter(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(payments)));
    }

    @Test
    @DisplayName("대상이 없으면 PG 조회 없이 끝난다.")
    void noTarget_doesNothing() {
        stubTarget();

        scheduler.reconcileDailySettlement();

        verifyNoInteractions(pgClient, reconciliationTaskWriter);
    }

    @Test
    @DisplayName("PAID 결제인데 PG가 충전 안 됐다고 하면(select success=false) 불일치로 기록한다.")
    void paidButPgSaysNotCharged_recordsMismatch() {
        Payment payment = payment(1L, PaymentStatus.PAID, "PG_KEY_1");
        stubTarget(payment);
        when(pgClient.select("PG_KEY_1")).thenReturn(new PgApproveResult(false, null));

        scheduler.reconcileDailySettlement();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.DAILY_SETTLEMENT_MISMATCH), eq(1L), anyString());
    }

    @Test
    @DisplayName("PAID 결제이고 PG도 충전됐다고 하면 일치 - 기록 없음.")
    void paidAndPgAgrees_noMismatch() {
        Payment payment = payment(2L, PaymentStatus.PAID, "PG_KEY_2");
        stubTarget(payment);
        when(pgClient.select("PG_KEY_2")).thenReturn(new PgApproveResult(true, LocalDateTime.now()));

        scheduler.reconcileDailySettlement();

        verifyNoInteractions(reconciliationTaskWriter);
    }

    @Test
    @DisplayName("FAILED 결제인데 PG는 여전히 충전됐다고 하면(돈 받고 환불 안 됨) 불일치로 기록한다.")
    void failedButPgSaysCharged_recordsMismatch() {
        Payment payment = payment(3L, PaymentStatus.FAILED, "PG_KEY_3");
        stubTarget(payment);
        when(pgClient.select("PG_KEY_3")).thenReturn(new PgApproveResult(true, LocalDateTime.now()));

        scheduler.reconcileDailySettlement();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.DAILY_SETTLEMENT_MISMATCH), eq(3L), anyString());
    }

    @Test
    @DisplayName("CANCELLED 결제이고 PG도 충전 안 됐다고 하면 일치 - 기록 없음.")
    void cancelledAndPgAgrees_noMismatch() {
        Payment payment = payment(4L, PaymentStatus.CANCELLED, "PG_KEY_4");
        stubTarget(payment);
        when(pgClient.select("PG_KEY_4")).thenReturn(new PgApproveResult(false, null));

        scheduler.reconcileDailySettlement();

        verifyNoInteractions(reconciliationTaskWriter);
    }

    @Test
    @DisplayName("paymentKey가 없는 결제(승인 전 FAILED)는 PG 조회 자체를 안 한다.")
    void noPaymentKey_skipsPgCall() {
        Payment payment = payment(5L, PaymentStatus.FAILED, null);
        stubTarget(payment);

        scheduler.reconcileDailySettlement();

        verifyNoInteractions(pgClient, reconciliationTaskWriter);
    }

    @Test
    @DisplayName("PG 재조회 자체가 실패(PgClientException)하면 판단 근거가 없으니 이번 배치는 건너뛴다.")
    void selectThrowsPgClientException_skipsWithoutRecording() {
        Payment payment = payment(6L, PaymentStatus.PAID, "PG_KEY_6");
        stubTarget(payment);
        when(pgClient.select("PG_KEY_6")).thenThrow(new PgClientException("NOT_FOUND", "결제가 존재하지 않음"));

        scheduler.reconcileDailySettlement();

        verifyNoInteractions(reconciliationTaskWriter);
    }

    @Test
    @DisplayName("PG 재조회 자체가 실패(네트워크 오류)해도 이번 배치는 건너뛴다.")
    void selectThrowsRestClientException_skipsWithoutRecording() {
        Payment payment = payment(7L, PaymentStatus.PAID, "PG_KEY_7");
        stubTarget(payment);
        when(pgClient.select("PG_KEY_7")).thenThrow(new RestClientException("timeout"));

        scheduler.reconcileDailySettlement();

        verifyNoInteractions(reconciliationTaskWriter);
    }

    @Test
    @DisplayName("한 건에서 예상치 못한 예외가 나도 나머지 결제는 계속 처리된다.")
    void unexpectedExceptionOnOnePayment_doesNotStopBatch() {
        Payment broken = payment(8L, PaymentStatus.PAID, "PG_KEY_8");
        Payment healthy = payment(9L, PaymentStatus.PAID, "PG_KEY_9");
        stubTarget(broken, healthy);
        when(pgClient.select("PG_KEY_8")).thenThrow(new RuntimeException("예상치 못한 오류"));
        when(pgClient.select("PG_KEY_9")).thenReturn(new PgApproveResult(false, null));

        scheduler.reconcileDailySettlement();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.DAILY_SETTLEMENT_MISMATCH), eq(9L), anyString());
        verify(reconciliationTaskWriter, never())
                .record(eq(ReconciliationTaskType.DAILY_SETTLEMENT_MISMATCH), eq(8L), anyString());
    }

    @Test
    @DisplayName("한 페이지를 넘는 대상은 다음 페이지까지 이어서 처리한다.")
    void multiplePages_processesAll() {
        Payment first = payment(10L, PaymentStatus.PAID, "PG_KEY_10");
        Payment second = payment(11L, PaymentStatus.PAID, "PG_KEY_11");
        Page<Payment> page0 = new PageImpl<>(List.of(first), org.springframework.data.domain.PageRequest.of(0, 1), 2);
        Page<Payment> page1 = new PageImpl<>(List.of(second), org.springframework.data.domain.PageRequest.of(1, 1), 2);
        when(paymentRepository.findByPaymentStatusInAndModifiedAtAfter(any(), any(), argThat(p -> p != null && p.getPageNumber() == 0)))
                .thenReturn(page0);
        when(paymentRepository.findByPaymentStatusInAndModifiedAtAfter(any(), any(), argThat(p -> p != null && p.getPageNumber() == 1)))
                .thenReturn(page1);
        when(pgClient.select("PG_KEY_10")).thenReturn(new PgApproveResult(false, null));
        when(pgClient.select("PG_KEY_11")).thenReturn(new PgApproveResult(false, null));

        scheduler.reconcileDailySettlement();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.DAILY_SETTLEMENT_MISMATCH), eq(10L), anyString());
        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.DAILY_SETTLEMENT_MISMATCH), eq(11L), anyString());
    }

    @Test
    @DisplayName("배치 자체가 실패(레포지토리 예외)하면 aggregateId 0으로 배치 실패를 기록한다.")
    void batchFails_recordsBatchFailure() {
        when(paymentRepository.findByPaymentStatusInAndModifiedAtAfter(any(), any(), any()))
                .thenThrow(new RuntimeException("DB 연결 실패"));

        scheduler.reconcileDailySettlement();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.DAILY_SETTLEMENT_MISMATCH), eq(0L), anyString());
    }
}
