package com.programmers.kdt.payment.scheduler;

import com.programmers.kdt.common.reconciliation.ReconciliationTaskType;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskWriter;
import com.programmers.kdt.payment.client.pg.PgApproveResult;
import com.programmers.kdt.payment.client.pg.PgCancelCommand;
import com.programmers.kdt.payment.client.pg.PgCancelResult;
import com.programmers.kdt.payment.client.pg.PgClient;
import com.programmers.kdt.payment.client.pg.PgClientException;
import com.programmers.kdt.payment.entity.Payment;
import com.programmers.kdt.payment.entity.PaymentStatus;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import com.programmers.kdt.payment.repository.PaymentRepository;
import com.programmers.kdt.payment.service.PointService;
import com.programmers.kdt.payment.service.tx.PaymentTxOps;
import com.programmers.kdt.payment.service.tx.PgOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class PaymentReconciliationSchedulerTest {

    @Mock
    private ReconciliationTaskWriter reconciliationTaskWriter;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PgClient pgClient;
    @Mock
    private PaymentTxOps paymentTxOps;
    @Mock
    private PointService pointService;

    private PaymentReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PaymentReconciliationScheduler(paymentRepository, pgClient, paymentTxOps, pointService, reconciliationTaskWriter);
    }

    private Payment pendingPayment(Long id, LocalDateTime modifiedAt) {
        Payment payment = Payment.create(1L, 10L, 10000L);
        payment.assignPaymentKey("PG_KEY_" + id);
        payment.markPending();
        ReflectionTestUtils.setField(payment, "id", id);
        ReflectionTestUtils.setField(payment, "modifiedAt", modifiedAt);
        return payment;
    }

    private void stubPending(Payment... payments) {
        when(paymentRepository.findByPaymentStatusAndModifiedAtBefore(eq(PaymentStatus.CONFIRM_PENDING_VERIFICATION), any(), any()))
                .thenReturn(new PageImpl<>(List.of(payments)));
    }

    @Test
    @DisplayName("대기 중인 결제가 없으면 아무 행동도 진행하지 않는다.")
    void noPending_doesNothing() {
        stubPending();

        scheduler.reconcilePayments();

        verifyNoInteractions(pgClient, paymentTxOps, pointService);
    }

    @Test
    @DisplayName("PG 재조회가 성공이면 PAID로 확정한다. (이벤트 발행은 applyReconcileResult 내부 책임 - PaymentTxOpsTest에서 검증)")
    void success_confirmAndPublishes() {
        Payment payment = pendingPayment(1L, LocalDateTime.now());
        stubPending(payment);
        when(pgClient.select("PG_KEY_1")).thenReturn(new PgApproveResult(true, LocalDateTime.now()));
        when(paymentTxOps.applyReconcileResult(1L, PgOutcome.SUCCESS)).thenReturn(payment);

        scheduler.reconcilePayments();

        verify(paymentTxOps).applyReconcileResult(1L, PgOutcome.SUCCESS);
        verifyNoInteractions(pointService);
    }

    @Test
    @DisplayName("PG 재조회가 success=false면 FAILED 처리 + 포인트 롤백")
    void fail_confirmAndPublishes() {
        Payment payment = pendingPayment(2L, LocalDateTime.now());
        stubPending(payment);
        when(pgClient.select("PG_KEY_2")).thenReturn(new PgApproveResult(false,
                null));
        when(paymentTxOps.applyReconcileResult(2L,
                PgOutcome.EXPLICIT_FAIL)).thenReturn(payment);
        when(pointService.findUsedAmount(anyString())).thenReturn(3000L);

        scheduler.reconcilePayments();

        verify(paymentTxOps).applyReconcileResult(2L, PgOutcome.EXPLICIT_FAIL);
        verify(pointService).rollbackPoint(anyString(), eq(3000L), anyString(),
                eq(true));
    }

    @Test
    @DisplayName("PG 재조회에서 PgClientException이 나면 FAILED로 확정한다.")
    void explicitFailByException() {
        Payment payment = pendingPayment(3L, LocalDateTime.now());
        stubPending(payment);
        when(pgClient.select("PG_KEY_3")).thenThrow(new PgClientException("NOT_FOUND", "결제가 존재하지 않음"));
        when(paymentTxOps.applyReconcileResult(3L, PgOutcome.EXPLICIT_FAIL)).thenReturn(payment);
        when(pointService.findUsedAmount(anyString())).thenReturn(0L);

        scheduler.reconcilePayments();

        verify(paymentTxOps).applyReconcileResult(3L, PgOutcome.EXPLICIT_FAIL);
        verify(pointService, never()).rollbackPoint(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("사용 포인트 조회가 null이면 0원으로 취급해 롤백을 호출하지 않는다.")
    void findUsedAmountNull_treatedZero() {
        Payment payment = pendingPayment(10L, LocalDateTime.now());
        stubPending(payment);
        when(pgClient.select("PG_KEY_10")).thenReturn(new PgApproveResult(false, null));
        when(paymentTxOps.applyReconcileResult(10L, PgOutcome.EXPLICIT_FAIL)).thenReturn(payment);
        when(pointService.findUsedAmount(anyString())).thenReturn(null);

        scheduler.reconcilePayments();

        verify(pointService, never()).rollbackPoint(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("임계값(10분)을 넘도록 계속 응답이 없으면 안전망으로 PG 취소를 시도하고 실패 처리로 확정짓는다.")
    void overThreshold_givesUpAndFails() {
        Payment payment = pendingPayment(5L, LocalDateTime.now().minusMinutes(10).minusSeconds(5));
        stubPending(payment);
        when(pgClient.select("PG_KEY_5")).thenThrow(new RestClientException("timeout"));
        when(pgClient.cancel(any())).thenReturn(new PgCancelResult(true, LocalDateTime.now()));
        when(paymentTxOps.applyReconcileResult(5L, PgOutcome.EXPLICIT_FAIL)).thenReturn(payment);
        when(pointService.findUsedAmount(anyString())).thenReturn(0L);

        scheduler.reconcilePayments();

        verify(pgClient).cancel(any());
        verify(paymentTxOps).applyReconcileResult(5L, PgOutcome.EXPLICIT_FAIL);
        verify(reconciliationTaskWriter, never())
                .record(eq(ReconciliationTaskType.PG_CONFIRM_TIMEOUT_CANCEL_UNCERTAIN), any(), anyString());
    }

    @Test
    @DisplayName("안전망 PG 취소 요청 자체가 실패(네트워크 오류)하면 CANCEL_UNCERTAIN으로 대사 테이블에 남긴다.")
    void giveUp_cancelRequestFails_recordsCancelUncertain() {
        Payment payment = pendingPayment(8L, LocalDateTime.now().minusMinutes(10).minusSeconds(5));
        stubPending(payment);
        when(pgClient.select("PG_KEY_8")).thenThrow(new RestClientException("timeout"));
        when(pgClient.cancel(any())).thenThrow(new RestClientException("cancel timeout"));
        when(paymentTxOps.applyReconcileResult(8L, PgOutcome.EXPLICIT_FAIL)).thenReturn(payment);
        when(pointService.findUsedAmount(anyString())).thenReturn(0L);

        scheduler.reconcilePayments();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.PG_CONFIRM_TIMEOUT_CANCEL_UNCERTAIN), eq(8L), anyString());
        verify(paymentTxOps).applyReconcileResult(8L, PgOutcome.EXPLICIT_FAIL);
    }

    @Test
    @DisplayName("안전망 PG 취소가 success=false로 응답해도 CANCEL_UNCERTAIN으로 남긴다.")
    void giveUp_cancelReturnsFalse_recordsCancelUncertain() {
        Payment payment = pendingPayment(9L, LocalDateTime.now().minusMinutes(10).minusSeconds(5));
        stubPending(payment);
        when(pgClient.select("PG_KEY_9")).thenThrow(new RestClientException("timeout"));
        when(pgClient.cancel(any())).thenReturn(new PgCancelResult(false, null));
        when(paymentTxOps.applyReconcileResult(9L, PgOutcome.EXPLICIT_FAIL)).thenReturn(payment);
        when(pointService.findUsedAmount(anyString())).thenReturn(0L);

        scheduler.reconcilePayments();

        verify(reconciliationTaskWriter)
                .record(eq(ReconciliationTaskType.PG_CONFIRM_TIMEOUT_CANCEL_UNCERTAIN), eq(9L), anyString());
    }

    @Test
    @DisplayName("안전망 PG 취소가 PgClientException(승인 전이었을 가능성)으로 거절되면 CANCEL_UNCERTAIN은 남기지 않는다.")
    void giveUp_cancelRejectedByPg_doesNotRecordCancelUncertain() {
        Payment payment = pendingPayment(11L, LocalDateTime.now().minusMinutes(10).minusSeconds(5));
        stubPending(payment);
        when(pgClient.select("PG_KEY_11")).thenThrow(new RestClientException("timeout"));
        when(pgClient.cancel(any())).thenThrow(new PgClientException("NOT_FOUND", "결제가 존재하지 않음"));
        when(paymentTxOps.applyReconcileResult(11L, PgOutcome.EXPLICIT_FAIL)).thenReturn(payment);
        when(pointService.findUsedAmount(anyString())).thenReturn(0L);

        scheduler.reconcilePayments();

        verify(reconciliationTaskWriter, never())
                .record(eq(ReconciliationTaskType.PG_CONFIRM_TIMEOUT_CANCEL_UNCERTAIN), any(), anyString());
        verify(paymentTxOps).applyReconcileResult(11L, PgOutcome.EXPLICIT_FAIL);
    }

    @Test
    @DisplayName("안전망 PG 취소 금액은 실제로 PG가 받은 금액(전체 금액 - 사용 포인트)이다.")
    void giveUp_cancelAmountExcludesUsedPoint() {
        Payment payment = pendingPayment(12L, LocalDateTime.now().minusMinutes(10).minusSeconds(5)); // amount=10000
        stubPending(payment);
        when(pgClient.select("PG_KEY_12")).thenThrow(new RestClientException("timeout"));
        when(pgClient.cancel(any())).thenReturn(new PgCancelResult(true, LocalDateTime.now()));
        when(paymentTxOps.applyReconcileResult(12L, PgOutcome.EXPLICIT_FAIL)).thenReturn(payment);
        when(pointService.findUsedAmount(anyString())).thenReturn(3000L);

        scheduler.reconcilePayments();

        ArgumentCaptor<PgCancelCommand> captor = ArgumentCaptor.forClass(PgCancelCommand.class);
        verify(pgClient).cancel(captor.capture());
        assertThat(captor.getValue().cancelAmount()).isEqualTo(7000L);
    }

    @Test
    @DisplayName("한 건에서 동시성 충돌(BusinessException)이 발생해도 나머지 결제는 계속 처리된다.")
    void concurrentModificationOnOnePayment_doesNotStopBatch() {
        Payment conflicted = pendingPayment(6L, LocalDateTime.now());
        Payment healthy = pendingPayment(7L, LocalDateTime.now());
        stubPending(conflicted, healthy);

        when(pgClient.select("PG_KEY_6")).thenReturn(new PgApproveResult(true, LocalDateTime.now()));
        when(paymentTxOps.applyReconcileResult(6L, PgOutcome.SUCCESS))
                .thenThrow(new com.programmers.kdt.common.exception.BusinessException(
                        PaymentErrorCode.PAYMENT_CONCURRENT_MODIFICATION));

        when(pgClient.select("PG_KEY_7")).thenReturn(new PgApproveResult(true, LocalDateTime.now()));
        when(paymentTxOps.applyReconcileResult(7L, PgOutcome.SUCCESS)).thenReturn(healthy);

        scheduler.reconcilePayments();

        verify(paymentTxOps).applyReconcileResult(6L, PgOutcome.SUCCESS);
        verify(paymentTxOps).applyReconcileResult(7L, PgOutcome.SUCCESS);
    }
}
