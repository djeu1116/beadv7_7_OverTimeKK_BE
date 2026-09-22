package com.programmers.kdt.payment.entity;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PaymentTest {

    @Nested
    @DisplayName("결제 생성")
    class CreatePayment {

        @Test
        @DisplayName("정상적인 orderId, amount로 생성하면 READY 상태로 초기화된다.")
        void createPayment() {
            //given
            Long orderId = 1L;
            Long userId = 1L;
            Long amount = 10000L;

            //when
            Payment payment = Payment.create(orderId, userId, amount);

            //then
            assertThat(payment.getOrderId()).isEqualTo(orderId);
            assertThat(payment.getAmount()).isEqualTo(amount);
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.READY);
            assertThat(payment.getRefundedAmount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("orderId가 null이면 예외가 발생한다.")
        void createPaymentNullOrderId() {
            assertThatThrownBy(() -> Payment.create(null, 1L ,10000L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("amount가 0이하이면 예외가 발생한다.")
        void createPaymentZeroAmount() {
            assertThatThrownBy(() -> Payment.create(1L, 1L, 0L))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> Payment.create(1L, 1L, -10000L))
                    .isInstanceOf(BusinessException.class);
        }

    }

    @Nested
    @DisplayName("결제 승인")
    class ApprovePayment {

        @Test
        @DisplayName("READY 상태에서 승인하면 PAID 상태로 전이된다.")
        void approvePayment() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);

            //when
            payment.approve();

            //then
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @Test
        @DisplayName("이미 PAID된 상태면 예외 없이 무시된다.")
        void dismissAlreadyPaid() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.approve();

            //when & then
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            payment.approve();

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @Test
        @DisplayName("READY가 아닌 상태에서 승인하면 예외가 발생한다.")
        void approvePaymentInvalidState() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.fail();

            //when & then
            assertThatThrownBy(payment::approve)
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("결제 실패")
    class FailPayment {

        @Test
        @DisplayName("READY 상태에서 실패 처리하면 FAILED로 전이된다.")
        void failPayment() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.fail();
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("이미 FAILED 상태면 예외 없이 무시된다.")
        void dismissAlreadyFailed() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.fail();

            //when & then
            payment.fail();
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("PAID 상태에서 실패 처리하면 예외가 발생한다.")
        void failPaymentInvalidState() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.approve();

            //when & then
            assertThatThrownBy(payment::fail)
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("환불 요청 (requestRefund)")
    class RequestRefund {

        @Test
        @DisplayName("PAID 상태에서 요청하면 REFUND_PENDING으로 전이된다.")
        void requestRefundSuccess() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.approve();

            //when
            payment.requestRefund();

            //then
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        }

        @Test
        @DisplayName("이미 REFUND_PENDING 상태에서 다시 요청하면 예외가 발생한다.")
        void requestRefundAlreadyInProgress() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.approve();
            payment.requestRefund();

            //when & then
            assertThatThrownBy(payment::requestRefund)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.REFUND_ALREADY_IN_PROGRESS);
        }

        @Test
        @DisplayName("READY 상태에서 요청하면 예외가 발생한다.")
        void requestRefundFromReady() {
            Payment payment = Payment.create(1L, 1L, 10000L);

            assertThatThrownBy(payment::requestRefund)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }

        @Test
        @DisplayName("이미 CANCELLED된 상태에서 요청하면 예외가 발생한다.")
        void requestRefundFromCancelled() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.approve();
            payment.requestRefund();
            payment.completeRefund(10000L);

            //when & then
            assertThatThrownBy(payment::requestRefund)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
    }

    @Nested
    @DisplayName("환불 처리 완료 (completeRefund)")
    class CompleteRefund {

        @Test
        @DisplayName("REFUND_PENDING 상태에서 전액 완료하면 CANCELLED로 전이되고 refundedAmount가 설정된다.")
        void completeRefundFull() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.approve();
            payment.requestRefund();

            //when
            payment.completeRefund(10000L);

            //then
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(payment.getRefundedAmount()).isEqualTo(10000L);
        }

        @Test
        @DisplayName("REFUND_PENDING 상태에서 부분 금액으로 완료해도 상태는 CANCELLED가 된다.")
        void completeRefundPartialAmount() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.approve();
            payment.requestRefund();

            //when
            payment.completeRefund(4000L);

            //then
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(payment.getRefundedAmount()).isEqualTo(4000L);
        }

        @Test
        @DisplayName("REFUND_PENDING 상태가 아니면 예외가 발생한다.")
        void completeRefundInvalidStatus() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.approve(); // PAID 상태, requestRefund() 안 거침

            //when & then
            assertThatThrownBy(() -> payment.completeRefund(10000L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }

        @Test
        @DisplayName("환불 금액이 null이거나 음수이면 예외가 발생한다.")
        void completeRefundInvalidAmount() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.approve();
            payment.requestRefund();

            //when & then
            assertThatThrownBy(() -> payment.completeRefund(null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_REFUND_AMOUNT);
            assertThatThrownBy(() -> payment.completeRefund(-1000L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_REFUND_AMOUNT);
        }

        @Test
        @DisplayName("환불 금액이 결제 금액을 초과하면 예외가 발생한다.")
        void completeRefundExceedsAmount() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.approve();
            payment.requestRefund();

            //when & then
            assertThatThrownBy(() -> payment.completeRefund(10001L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_REFUND_AMOUNT);
        }
    }

    @Nested
    @DisplayName("환불 처리 실패 롤백 (failRefund)")
    class FailRefund {

        @Test
        @DisplayName("REFUND_PENDING 상태에서 실패 처리하면 PAID로 복귀한다.")
        void failRefundRollback() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.approve();
            payment.requestRefund();

            //when
            payment.failRefund();

            //then
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @Test
        @DisplayName("REFUND_PENDING 상태가 아니면 아무 변화 없이 무시한다.")
        void failRefundIgnoredWhenNotPending() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.approve(); // PAID 상태

            //when
            payment.failRefund();

            //then
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @Test
        @DisplayName("이미 CANCELLED 상태여도 무시하고 예외를 던지지 않는다.")
        void failRefundIgnoredWhenCancelled() {
            //given
            Payment payment = Payment.create(1L, 1L, 10000L);
            payment.approve();
            payment.requestRefund();
            payment.completeRefund(10000L);

            //when
            payment.failRefund();

            //then
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        }
    }
}