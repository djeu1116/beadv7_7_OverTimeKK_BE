package com.programmers.kdt.payment.application.scheduler;

import com.programmers.kdt.common.reconciliation.ReconciliationTaskType;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskWriter;
import com.programmers.kdt.payment.infrastructure.client.pg.PgApproveResult;
import com.programmers.kdt.payment.infrastructure.client.pg.PgClient;
import com.programmers.kdt.payment.infrastructure.client.pg.PgClientException;
import com.programmers.kdt.payment.infrastructure.client.pg.PgResponseMismatchException;
import com.programmers.kdt.payment.domain.entity.Payment;
import com.programmers.kdt.payment.domain.entity.PaymentAttempt;
import com.programmers.kdt.payment.domain.entity.PaymentStatus;
import com.programmers.kdt.payment.infrastructure.repository.PaymentAttemptRepository;
import com.programmers.kdt.payment.infrastructure.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentDailySettlementReconciliationSchedulerTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;
    @Mock
    private PgClient pgClient;
    @Mock
    private ReconciliationTaskWriter reconciliationTaskWriter;

    private PaymentDailySettlementReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PaymentDailySettlementReconciliationScheduler(
                paymentRepository, paymentAttemptRepository, pgClient, reconciliationTaskWriter);
        // 대부분의 테스트는 정확한 사용 포인트 값에 관심이 없으니 usedPoint=0인 attempt를 기본으로 깔아둔다.
        // 필요한 테스트만 따로 재스텁한다.
        lenient().when(paymentAttemptRepository.findByPaymentIdAndAttemptSeq(any(), anyInt()))
                .thenReturn(Optional.of(PaymentAttempt.create(1L, 0, "PG_ORDER", 0L)));
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
        when(pgClient.select(eq("PG_KEY_1"), any(), any())).thenReturn(new PgApproveResult(false, null));

        scheduler.reconcileDailySettlement();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.DAILY_SETTLEMENT_MISMATCH), eq(1L), anyString());
    }

    @Test
    @DisplayName("PAID 결제이고 PG도 충전됐다고 하면 일치 - 기록 없음.")
    void paidAndPgAgrees_noMismatch() {
        Payment payment = payment(2L, PaymentStatus.PAID, "PG_KEY_2");
        stubTarget(payment);
        when(pgClient.select(eq("PG_KEY_2"), any(), any())).thenReturn(new PgApproveResult(true, LocalDateTime.now()));

        scheduler.reconcileDailySettlement();

        verifyNoInteractions(reconciliationTaskWriter);
    }

    @Test
    @DisplayName("FAILED 결제인데 PG는 여전히 충전됐다고 하면(돈 받고 환불 안 됨) 불일치로 기록한다.")
    void failedButPgSaysCharged_recordsMismatch() {
        Payment payment = payment(3L, PaymentStatus.FAILED, "PG_KEY_3");
        stubTarget(payment);
        when(pgClient.select(eq("PG_KEY_3"), any(), any())).thenReturn(new PgApproveResult(true, LocalDateTime.now()));

        scheduler.reconcileDailySettlement();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.DAILY_SETTLEMENT_MISMATCH), eq(3L), anyString());
    }

    @Test
    @DisplayName("CANCELLED 결제이고 PG도 충전 안 됐다고 하면 일치 - 기록 없음.")
    void cancelledAndPgAgrees_noMismatch() {
        Payment payment = payment(4L, PaymentStatus.CANCELLED, "PG_KEY_4");
        stubTarget(payment);
        when(pgClient.select(eq("PG_KEY_4"), any(), any())).thenReturn(new PgApproveResult(false, null));

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
        when(pgClient.select(eq("PG_KEY_6"), any(), any())).thenThrow(new PgClientException("NOT_FOUND", "결제가 존재하지 않음"));

        scheduler.reconcileDailySettlement();

        verifyNoInteractions(reconciliationTaskWriter);
    }

    @Test
    @DisplayName("PG 재조회 자체가 실패(네트워크 오류)해도 이번 배치는 건너뛴다.")
    void selectThrowsRestClientException_skipsWithoutRecording() {
        Payment payment = payment(7L, PaymentStatus.PAID, "PG_KEY_7");
        stubTarget(payment);
        when(pgClient.select(eq("PG_KEY_7"), any(), any())).thenThrow(new RestClientException("timeout"));

        scheduler.reconcileDailySettlement();

        verifyNoInteractions(reconciliationTaskWriter);
    }

    @Test
    @DisplayName("한 건에서 예상치 못한 예외가 나도 나머지 결제는 계속 처리된다.")
    void unexpectedExceptionOnOnePayment_doesNotStopBatch() {
        Payment broken = payment(8L, PaymentStatus.PAID, "PG_KEY_8");
        Payment healthy = payment(9L, PaymentStatus.PAID, "PG_KEY_9");
        stubTarget(broken, healthy);
        when(pgClient.select(eq("PG_KEY_8"), any(), any())).thenThrow(new RuntimeException("예상치 못한 오류"));
        when(pgClient.select(eq("PG_KEY_9"), any(), any())).thenReturn(new PgApproveResult(false, null));

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
        when(pgClient.select(eq("PG_KEY_10"), any(), any())).thenReturn(new PgApproveResult(false, null));
        when(pgClient.select(eq("PG_KEY_11"), any(), any())).thenReturn(new PgApproveResult(false, null));

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

    @Test
    @DisplayName("attempt 이력이 없으면 기대 금액을 못 구하니 PG 조회 자체를 건너뛴다.")
    void noAttempt_skipsPgCall() {
        Payment payment = payment(12L, PaymentStatus.PAID, "PG_KEY_12");
        stubTarget(payment);
        when(paymentAttemptRepository.findByPaymentIdAndAttemptSeq(any(), anyInt())).thenReturn(Optional.empty());

        scheduler.reconcileDailySettlement();

        verifyNoInteractions(pgClient, reconciliationTaskWriter);
    }

    @Test
    @DisplayName("select() 호출 시 기대 orderId는 payment의 pgOrderId, 기대 금액은 attempt에 남은 사용 포인트를 뺀 값이다.")
    void select_usesExpectedOrderIdAndAmount() {
        Payment payment = payment(13L, PaymentStatus.PAID, "PG_KEY_13"); // amount=10000
        ReflectionTestUtils.setField(payment, "pgOrderId", "PG_ORDER_13");
        stubTarget(payment);
        when(paymentAttemptRepository.findByPaymentIdAndAttemptSeq(13L, 0))
                .thenReturn(Optional.of(PaymentAttempt.create(13L, 0, "PG_ORDER_13", 3000L)));
        when(pgClient.select(eq("PG_KEY_13"), any(), any())).thenReturn(new PgApproveResult(true, LocalDateTime.now()));

        scheduler.reconcileDailySettlement();

        ArgumentCaptor<Long> amountCaptor = ArgumentCaptor.forClass(Long.class);
        verify(pgClient).select(eq("PG_KEY_13"), eq("PG_ORDER_13"), amountCaptor.capture());
        assertThat(amountCaptor.getValue()).isEqualTo(7000L);
    }

    @Test
    @DisplayName("PG가 성공(DONE)이라 답했는데 orderId/금액이 기대값과 다르면(PgResponseMismatchException) 그 자체를 불일치로 기록한다.")
    void selectThrowsResponseMismatch_recordsMismatch() {
        Payment payment = payment(14L, PaymentStatus.PAID, "PG_KEY_14");
        stubTarget(payment);
        when(pgClient.select(eq("PG_KEY_14"), any(), any()))
                .thenThrow(new PgResponseMismatchException("PG_KEY_14", "EXPECTED_ORDER", 10000L, "OTHER_ORDER", 10000L));

        scheduler.reconcileDailySettlement();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.DAILY_SETTLEMENT_MISMATCH), eq(14L), anyString());
    }
}
