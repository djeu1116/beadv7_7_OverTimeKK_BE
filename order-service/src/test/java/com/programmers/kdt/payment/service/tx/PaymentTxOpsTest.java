package com.programmers.kdt.payment.service.tx;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.entity.Payment;
import com.programmers.kdt.payment.entity.PaymentStatus;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import com.programmers.kdt.payment.repository.PaymentRepository;
import com.programmers.kdt.payment.service.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private PointService pointService;

    private PaymentTxOps paymentTxOps;

    @BeforeEach
    void setUp() {
        paymentTxOps = new PaymentTxOps(paymentRepository, pointService);
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

            PaymentTxOps.ReadyPaymentContext result = paymentTxOps.assignKeyAndCommit(1L, "PG_KEY_1");

            assertThat(result.payment().getPaymentKey()).isEqualTo("PG_KEY_1");
            assertThat(result.payment().getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRM_PENDING_VERIFICATION);
            assertThat(result.usedPoint()).isEqualTo(3000L);
            verify(paymentRepository).saveAndFlush(payment);
        }

        @Test
        @DisplayName("사용한 포인트가 없으면(null) 0으로 대체해서 반환한다.")
        void noUsedPoint_defaultsToZero() {
            Payment payment = readyPayment(1L);
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(pointService.findUsedAmount(any())).thenReturn(null);

            PaymentTxOps.ReadyPaymentContext result = paymentTxOps.assignKeyAndCommit(1L, "PG_KEY_1");

            assertThat(result.usedPoint()).isEqualTo(0L);
        }

        @Test
        @DisplayName("결제를 찾을 수 없으면 예외가 발생하고 저장하지 않는다.")
        void notFound_throwsAndNeverSaves() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentTxOps.assignKeyAndCommit(1L, "PG_KEY_1"))
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

            assertThatThrownBy(() -> paymentTxOps.assignKeyAndCommit(1L, "PG_KEY_1"))
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

            assertThatThrownBy(() -> paymentTxOps.assignKeyAndCommit(1L, "PG_KEY_1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_CONCURRENT_MODIFICATION);
        }
    }

    @Nested
    @DisplayName("tx2: applyConfirmResult")
    class ApplyConfirmResult {

        @Test
        @DisplayName("SUCCESS면 PAID로 확정하고 저장한다.")
        void success_confirmsAndSaves() {
            Payment payment = readyPayment(1L);
            payment.markPending();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            Payment result = paymentTxOps.applyConfirmResult(1L, PgOutcome.SUCCESS);

            assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            verify(paymentRepository).saveAndFlush(payment);
        }

        @Test
        @DisplayName("EXPLICIT_FAIL이면 FAILED로 확정하고 저장한다.")
        void explicitFail_failsAndSaves() {
            Payment payment = readyPayment(1L);
            payment.markPending();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            Payment result = paymentTxOps.applyConfirmResult(1L, PgOutcome.EXPLICIT_FAIL);

            assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(paymentRepository).saveAndFlush(payment);
        }

        @Test
        @DisplayName("AMBIGUOUS면 상태는 그대로 두지만, applyReconcileResult와 달리 saveAndFlush는 그대로 호출된다.")
        void ambiguous_noStateChangeButStillSaves() {
            Payment payment = readyPayment(1L);
            payment.markPending();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            Payment result = paymentTxOps.applyConfirmResult(1L, PgOutcome.AMBIGUOUS);

            assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRM_PENDING_VERIFICATION);
            verify(paymentRepository).saveAndFlush(payment);
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
        @DisplayName("AMBIGUOUS면 저장 없이 조회한 상태 그대로 반환한다.")
        void ambiguous_skipsSave() {
            Payment payment = readyPayment(1L);
            payment.markPending();
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            Payment result = paymentTxOps.applyReconcileResult(1L, PgOutcome.AMBIGUOUS);

            assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRM_PENDING_VERIFICATION);
            verify(paymentRepository, never()).saveAndFlush(any());
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
