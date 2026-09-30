package com.programmers.kdt.payment.application.service.tx;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.contract.PaymentConfirmEvent;
import com.programmers.kdt.common.contract.PaymentFailEvent;
import com.programmers.kdt.payment.domain.entity.Payment;
import com.programmers.kdt.payment.domain.entity.PaymentAttempt;
import com.programmers.kdt.payment.domain.entity.PaymentAttemptStatus;
import com.programmers.kdt.payment.domain.entity.PaymentStatus;
import com.programmers.kdt.payment.domain.entity.outbox.OutboxEventType;
import com.programmers.kdt.payment.domain.exception.PaymentErrorCode;
import com.programmers.kdt.payment.infrastructure.repository.PaymentAttemptRepository;
import com.programmers.kdt.payment.infrastructure.repository.PaymentRepository;
import com.programmers.kdt.payment.application.service.OutboxEventWriter;
import com.programmers.kdt.payment.application.service.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentTxOpsTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;
    @Mock
    private PointService pointService;
    @Mock
    private OutboxEventWriter outboxEventWriter;

    private PaymentTxOps paymentTxOps;

    @BeforeEach
    void setUp() {
        paymentTxOps = new PaymentTxOps(paymentRepository, paymentAttemptRepository, pointService, outboxEventWriter);
    }

    private Payment readyPayment(Long id) {
        Payment payment = Payment.create(1L, 10L, 10000L);
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }

    @Nested
    @DisplayName("tx1: assignKeyAndCommit")
    class AssignKeyAndCommit {

        @Test
        @DisplayName("READY 결제는 paymentKey를 부여받고, PG 호출 전에 CONFIRM_PENDING_VERIFICATION으로 즉시 커밋된다.")
        void commitsPendingBeforePgCall() {
            Payment payment = readyPayment(1L);
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(pointService.findUsedAmount(any())).thenReturn(3000L);

            PaymentTxOps.ReadyPaymentContext result = paymentTxOps.assignKeyAndCommit(1L, "PG_KEY_1", 10L);

            assertThat(result.payment().getPaymentKey()).isEqualTo("PG_KEY_1");
            assertThat(result.payment().getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRM_PENDING_VERIFICATION);
            assertThat(result.usedPoint()).isEqualTo(3000L);
            verify(paymentRepository).saveAndFlush(payment);
        }

        @Test
        @DisplayName("현재 시도(attempt) 행이 있으면 그 행에도 paymentKey가 반영되고 PENDING_VERIFICATION으로 바뀐다.")
        void updatesCurrentAttemptTooWhenPresent() {
            Payment payment = readyPayment(1L);
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(pointService.findUsedAmount(any())).thenReturn(0L);
            PaymentAttempt attempt = PaymentAttempt.create(1L, 0, "PG_ORDER_1", 0L);
            when(paymentAttemptRepository.findByPaymentIdAndAttemptSeq(1L, 0)).thenReturn(Optional.of(attempt));

            paymentTxOps.assignKeyAndCommit(1L, "PG_KEY_1", 10L);

            assertThat(attempt.getPaymentKey()).isEqualTo("PG_KEY_1");
            assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PENDING_VERIFICATION);
            verify(paymentAttemptRepository).save(attempt);
        }

        @Test
        @DisplayName("현재 시도 행이 없어도(예: 마이그레이션 이전 데이터) 예외 없이 그냥 넘어간다.")
        void noCurrentAttempt_doesNotFail() {
            Payment payment = readyPayment(1L);
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(pointService.findUsedAmount(any())).thenReturn(0L);
            when(paymentAttemptRepository.findByPaymentIdAndAttemptSeq(1L, 0)).thenReturn(Optional.empty());

            assertThatCode(() -> paymentTxOps.assignKeyAndCommit(1L, "PG_KEY_1", 10L)).doesNotThrowAnyException();

            verify(paymentAttemptRepository, never()).save(any());
        }

        @Test
        @DisplayName("사용한 포인트가 없으면(null) 0으로 대체해서 반환한다.")
        void noUsedPoint_defaultsToZero() {
            Payment payment = readyPayment(1L);
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(pointService.findUsedAmount(any())).thenReturn(null);

            PaymentTxOps.ReadyPaymentContext result = paymentTxOps.assignKeyAndCommit(1L, "PG_KEY_1", 10L);

            assertThat(result.usedPoint()).isEqualTo(0L);
        }

        @Test
        @DisplayName("결제를 찾을 수 없으면 예외가 발생하고 저장하지 않는다.")
        void notFound_throwsAndNeverSaves() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentTxOps.assignKeyAndCommit(1L, "PG_KEY_1", 10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);

            verify(paymentRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("READY 상태가 아니면 예외가 발생하고 저장하지 않는다.")
        void notReady_throwsAndNeverSaves() {
            Payment payment = readyPayment(1L);
            payment.fail();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentTxOps.assignKeyAndCommit(1L, "PG_KEY_1", 10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);

            verify(paymentRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("커밋 시점에 다른 트랜잭션과 낙관적락이 충돌하면 PAYMENT_CONCURRENT_MODIFICATION으로 변환된다.")
        void optimisticLockConflict_throwsConcurrentModification() {
            Payment payment = readyPayment(1L);
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(paymentRepository.saveAndFlush(payment))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Payment.class, 1L));

            assertThatThrownBy(() -> paymentTxOps.assignKeyAndCommit(1L, "PG_KEY_1", 10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_CONCURRENT_MODIFICATION);
        }

        @Test
        @DisplayName("소유자가 다르면 상태를 바꾸거나 커밋하기 전에 예외가 발생한다.")
        void ownerMismatch_throwsBeforeAnyMutation() {
            Payment payment = readyPayment(1L); // userId=10L로 생성됨
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentTxOps.assignKeyAndCommit(1L, "ATTACKER_KEY", 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_ACCESS_DENIED);

            // 예외가 나기 전에 이미 커밋됐다면 여기서 상태/키가 이미 바뀌어 있었을 것
            assertThat(payment.getPaymentKey()).isNull();
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.READY);
            verify(paymentRepository, never()).saveAndFlush(any());
            verifyNoInteractions(pointService);
        }
    }

    @Nested
    @DisplayName("tx2: applyConfirmResult")
    class ApplyConfirmResult {

        @Test
        @DisplayName("SUCCESS면 PAID로 확정하고 저장하고, PAYMENT_CONFIRMED를 outbox에 기록하고, 현재 attempt도 PAID로 남는다.")
        void success_confirmsAndSaves() {
            Payment payment = readyPayment(1L);
            payment.markPending();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            PaymentAttempt attempt = PaymentAttempt.create(1L, 0, "PG_ORDER_1", 0L);
            when(paymentAttemptRepository.findByPaymentIdAndAttemptSeq(1L, 0)).thenReturn(Optional.of(attempt));

            Payment result = paymentTxOps.applyConfirmResult(1L, PgOutcome.SUCCESS);

            assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PAID);
            verify(paymentRepository).saveAndFlush(payment);

            ArgumentCaptor<PaymentConfirmEvent> captor = ArgumentCaptor.forClass(PaymentConfirmEvent.class);
            verify(outboxEventWriter).enqueue(eq(OutboxEventType.PAYMENT_CONFIRMED), eq(1L), captor.capture());
            assertThat(captor.getValue().orderId()).isEqualTo(payment.getOrderId());
            assertThat(captor.getValue().paymentId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("EXPLICIT_FAIL이면 FAILED로 확정하고 저장하고, PAYMENT_FAILED를 outbox에 기록하고, 현재 attempt도 FAILED로 남는다.")
        void explicitFail_failsAndSaves() {
            Payment payment = readyPayment(1L);
            payment.markPending();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            PaymentAttempt attempt = PaymentAttempt.create(1L, 0, "PG_ORDER_1", 0L);
            when(paymentAttemptRepository.findByPaymentIdAndAttemptSeq(1L, 0)).thenReturn(Optional.of(attempt));

            Payment result = paymentTxOps.applyConfirmResult(1L, PgOutcome.EXPLICIT_FAIL);

            assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
            verify(paymentRepository).saveAndFlush(payment);
            verify(outboxEventWriter).enqueue(eq(OutboxEventType.PAYMENT_FAILED), eq(1L), any(PaymentFailEvent.class));
        }

        @Test
        @DisplayName("이미 PAID인 결제에 SUCCESS가 다시 오면 중복 무시되고, outbox에도 다시 기록하지 않는다.")
        void alreadyPaid_doesNotEnqueueAgain() {
            Payment payment = readyPayment(1L);
            payment.markPending();
            payment.confirmVerifiedSuccess();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            paymentTxOps.applyConfirmResult(1L, PgOutcome.SUCCESS);

            verifyNoInteractions(outboxEventWriter);
        }

        @Test
        @DisplayName("AMBIGUOUS면 상태는 그대로 두지만, applyReconcileResult와 달리 saveAndFlush는 그대로 호출되고, outbox에는 기록하지 않는다.")
        void ambiguous_noStateChangeButStillSaves() {
            Payment payment = readyPayment(1L);
            payment.markPending();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            Payment result = paymentTxOps.applyConfirmResult(1L, PgOutcome.AMBIGUOUS);

            assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRM_PENDING_VERIFICATION);
            verify(paymentRepository).saveAndFlush(payment);
            verifyNoInteractions(outboxEventWriter);
        }

        @Test
        @DisplayName("PG 응답 반영 시점에 낙관적락이 충돌하면 PAYMENT_CONCURRENT_MODIFICATION으로 변환된다.")
        void optimisticLockConflict_throwsConcurrentModification() {
            Payment payment = readyPayment(1L);
            payment.markPending();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(paymentRepository.saveAndFlush(payment))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Payment.class, 1L));

            assertThatThrownBy(() -> paymentTxOps.applyConfirmResult(1L, PgOutcome.SUCCESS))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_CONCURRENT_MODIFICATION);
        }
    }

    @Nested
    @DisplayName("재조회: applyReconcileResult")
    class ApplyReconcileResult {

        @Test
        @DisplayName("AMBIGUOUS면 저장 없이 조회한 상태 그대로 반환하고, outbox에도 기록하지 않는다.")
        void ambiguous_skipsSave() {
            Payment payment = readyPayment(1L);
            payment.markPending();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            Payment result = paymentTxOps.applyReconcileResult(1L, PgOutcome.AMBIGUOUS);

            assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRM_PENDING_VERIFICATION);
            verify(paymentRepository, never()).saveAndFlush(any());
            verifyNoInteractions(outboxEventWriter);
        }

        @Test
        @DisplayName("SUCCESS면 PAID로 확정하고 PAYMENT_CONFIRMED를 outbox에 기록한다.")
        void success_confirmsAndEnqueues() {
            Payment payment = readyPayment(1L);
            payment.markPending();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            Payment result = paymentTxOps.applyReconcileResult(1L, PgOutcome.SUCCESS);

            assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            verify(outboxEventWriter).enqueue(eq(OutboxEventType.PAYMENT_CONFIRMED), eq(1L), any(PaymentConfirmEvent.class));
        }

        @Test
        @DisplayName("EXPLICIT_FAIL이면 FAILED로 확정하고 PAYMENT_FAILED를 outbox에 기록한다.")
        void explicitFail_failsAndEnqueues() {
            Payment payment = readyPayment(1L);
            payment.markPending();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            Payment result = paymentTxOps.applyReconcileResult(1L, PgOutcome.EXPLICIT_FAIL);

            assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(outboxEventWriter).enqueue(eq(OutboxEventType.PAYMENT_FAILED), eq(1L), any(PaymentFailEvent.class));
        }

        @Test
        @DisplayName("재조회 중 낙관적락이 충돌하면(API 경로와 경합) PAYMENT_CONCURRENT_MODIFICATION으로 변환된다.")
        void optimisticLockConflict_throwsConcurrentModification() {
            Payment payment = readyPayment(1L);
            payment.markPending();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(paymentRepository.saveAndFlush(payment))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Payment.class, 1L));

            assertThatThrownBy(() -> paymentTxOps.applyReconcileResult(1L, PgOutcome.SUCCESS))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_CONCURRENT_MODIFICATION);
        }
    }
}
