package com.programmers.kdt.payment.application.scheduler;

import com.programmers.kdt.common.contract.PaymentConfirmEvent;
import com.programmers.kdt.common.contract.PaymentFailEvent;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskType;
import com.programmers.kdt.payment.infrastructure.client.pg.MockPgClient;
import com.programmers.kdt.payment.infrastructure.client.pg.PgApproveResult;
import com.programmers.kdt.payment.infrastructure.client.pg.PgCancelCommand;
import com.programmers.kdt.payment.domain.entity.Payment;
import com.programmers.kdt.payment.domain.entity.PaymentStatus;
import com.programmers.kdt.payment.domain.entity.outbox.OutboxEventType;
import com.programmers.kdt.payment.infrastructure.repository.PaymentAttemptRepository;
import com.programmers.kdt.payment.infrastructure.repository.PaymentRepository;
import com.programmers.kdt.payment.application.service.OutboxEventWriter;
import com.programmers.kdt.payment.application.service.PointService;
import com.programmers.kdt.common.reconciliation.ReconciliationTaskWriter;
import com.programmers.kdt.payment.application.service.tx.PaymentTxOps;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DataJpaTest
public class PaymentReconciliationIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EntityManager em;

    private final MockPgClient mockPgClient = new MockPgClient();
    private final PointService pointService = mock(PointService.class);
    private final OutboxEventWriter outboxEventWriter = mock(OutboxEventWriter.class);
    private final ReconciliationTaskWriter reconciliationTaskWriter = mock(ReconciliationTaskWriter.class);
    private final PaymentAttemptRepository paymentAttemptRepository = mock(PaymentAttemptRepository.class);

    private PaymentReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        mockPgClient.reset();
        PaymentTxOps paymentTxOps = new PaymentTxOps(paymentRepository, paymentAttemptRepository, pointService, outboxEventWriter);
        scheduler = new PaymentReconciliationScheduler(
                paymentRepository, mockPgClient, paymentTxOps, pointService, reconciliationTaskWriter);
    }

    private Payment persistPendingPayment(String paymentKey) {
        return persistPendingPayment(paymentKey, java.time.Duration.ofMinutes(1));
    }

    private Payment persistPendingPayment(String paymentKey, java.time.Duration age) {
        Payment payment = Payment.create(1L, 10L, 10000L);
        payment.assignPaymentKey(paymentKey);
        payment.markPending();
        paymentRepository.saveAndFlush(payment);
        em.createQuery("update Payment p set p.modifiedAt = :t where p.id = :id")
                .setParameter("t", LocalDateTime.now().minus(age))
                .setParameter("id", payment.getId())
                .executeUpdate();
        em.clear();
        return payment;
    }

    @Test
    @DisplayName("실제 DB 기준으로, PG 재조회 성공 시 PAID 확정, 승인 이벤트가 outbox에 기록된다.")
    void reconcile_success_confirmPayment() {
        Payment pending = persistPendingPayment("PG_KEY_OK");
        mockPgClient.stubSelect("PG_KEY_OK", () -> new PgApproveResult(true, LocalDateTime.now()));

        scheduler.reconcilePayments();

        Payment result = paymentRepository.findById(pending.getId()).orElseThrow();
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(outboxEventWriter).enqueue(eq(OutboxEventType.PAYMENT_CONFIRMED), eq(result.getId()),
                eq(new PaymentConfirmEvent(result.getOrderId(), result.getId())));

    }

    @Test
    @DisplayName("실제 DB 기준으로, PG 재조회 실패 시 FAILED 확정, 실패 이벤트가 outbox에 기록되고 포인트가 롤백된다.")
    void reconcile_explicitFail_failPaymentAndRollback() {
        Payment pending = persistPendingPayment("PG_KEY_FAIL");
        mockPgClient.stubSelect("PG_KEY_FAIL", () -> new PgApproveResult(false, null));
        when(pointService.findUsedAmount(anyString())).thenReturn(3000L);

        scheduler.reconcilePayments();

        Payment result = paymentRepository.findById(pending.getId()).orElseThrow();
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(pointService).rollbackPoint(anyString(), eq(3000L), anyString(), eq(true));
        verify(outboxEventWriter).enqueue(eq(OutboxEventType.PAYMENT_FAILED), eq(result.getId()), any(PaymentFailEvent.class));
    }

    @Test
    @DisplayName("PG가 계속 응답 없으면 AMBIGUOUS 상태와 수정시각 모두 그대로 유지된다.")
    void reconcile_stillAmbiguous_leavesRowUntouched() {
        Payment pending = persistPendingPayment("PG_KEY_TIMEOUT");
        LocalDateTime originModifiedAt = paymentRepository.findById(pending.getId()).orElseThrow().getModifiedAt();
        mockPgClient.stubSelect("PG_KEY_TIMEOUT", () -> {
            throw new RestClientException("simulated timeout");}
        );

        scheduler.reconcilePayments();

        Payment result = paymentRepository.findById(pending.getId()).orElseThrow();
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRM_PENDING_VERIFICATION);
        assertThat(result.getModifiedAt()).isEqualTo(originModifiedAt);
        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    @DisplayName("MIN_PENDING_AGE(40초) 미만으로 대기 중인 결제는 이번 배치에서 건드리지 않는다.")
    void reconcile_underMinPendingAge_skipsPayment() {
        Payment pending = persistPendingPayment("PG_KEY_YOUNG", java.time.Duration.ofSeconds(30));
        mockPgClient.stubSelect("PG_KEY_YOUNG", () -> new PgApproveResult(true, LocalDateTime.now()));

        scheduler.reconcilePayments();

        Payment result = paymentRepository.findById(pending.getId()).orElseThrow();
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRM_PENDING_VERIFICATION);
        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    @DisplayName("MIN_PENDING_AGE(40초)를 넘겨 대기 중인 결제는 이번 배치에 포함된다.")
    void reconcile_overMinPendingAge_includesPayment() {
        Payment pending = persistPendingPayment("PG_KEY_OLD", java.time.Duration.ofSeconds(45));
        mockPgClient.stubSelect("PG_KEY_OLD", () -> new PgApproveResult(true, LocalDateTime.now()));

        scheduler.reconcilePayments();

        Payment result = paymentRepository.findById(pending.getId()).orElseThrow();
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(outboxEventWriter).enqueue(eq(OutboxEventType.PAYMENT_CONFIRMED), eq(result.getId()),
                eq(new PaymentConfirmEvent(result.getOrderId(), result.getId())));
    }

    @Test
    @DisplayName("실제 DB 기준으로, 재조회 포기(GIVE_UP_THRESHOLD 10분 초과) 시 안전망 PG 취소를 시도한 뒤 FAILED로 확정, 포인트 롤백, 대사 테이블 기록까지 이어진다.")
    void reconcile_givesUp_attemptsCancelThenFailsAndRollsBack() {
        Payment pending = persistPendingPayment("PG_KEY_GIVE_UP", java.time.Duration.ofMinutes(11));
        mockPgClient.stubSelect("PG_KEY_GIVE_UP", () -> { throw new RestClientException("simulated timeout"); });
        when(pointService.findUsedAmount(anyString())).thenReturn(2000L);

        scheduler.reconcilePayments();

        Payment result = paymentRepository.findById(pending.getId()).orElseThrow();
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(mockPgClient.getCancelCalls()).hasSize(1);
        PgCancelCommand cancelCall = mockPgClient.getCancelCalls().get(0);
        assertThat(cancelCall.transactionKey()).isEqualTo("PG_KEY_GIVE_UP");
        assertThat(cancelCall.cancelAmount()).isEqualTo(8000L); // amount(10000) - usedPoint(2000)
        verify(pointService).rollbackPoint(anyString(), eq(2000L), anyString(), eq(true));
        verify(reconciliationTaskWriter).record(eq(ReconciliationTaskType.PG_CONFIRM_TIMEOUT_FORCE_FAILED), eq(result.getId()), anyString());
    }
}
