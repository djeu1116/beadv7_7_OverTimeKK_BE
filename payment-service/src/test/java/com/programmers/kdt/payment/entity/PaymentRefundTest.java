package com.programmers.kdt.payment.entity;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PaymentRefundTest {


    @Nested
    @DisplayName("PaymentRefund 생성")
    class Create {

        @Test
        @DisplayName("유효한 값이면 정상 생성된다.")
        void createSuccess() {
            PaymentRefund refund = PaymentRefund.create(1L, 1000L, "고객 변심");

            assertThat(refund.getPaymentId()).isEqualTo(1L);
            assertThat(refund.getRefundAmount()).isEqualTo(1000L);
            assertThat(refund.getReason()).isEqualTo("고객 변심");
        }

        @Test
        @DisplayName("reason 없어도 정상 생성된다.")
        void createSuccessWithoutReason() {
            PaymentRefund refund = PaymentRefund.create(1L, 1000L, null);

            assertThat(refund.getPaymentId()).isEqualTo(1L);
            assertThat(refund.getRefundAmount()).isEqualTo(1000L);
            assertThat(refund.getReason()).isNull();
        }

        @Test
        @DisplayName("paymentId가 없으면 예외를 발생시킨다.")
        void createWithoutPaymentId() {
            assertThatThrownBy(() -> PaymentRefund.create(null, 1000L, "고객 변심"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.MISSING_PAYMENT_ID);
        }

        @Test
        @DisplayName("환불 금액이 null이면 예외가 발생한다.")
        void createWithNullAmount() {
            assertThatThrownBy(() -> PaymentRefund.create(1L, null, "고객 변심"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.ZERO_REFUND_AMOUNT);
        }

        @Test
        @DisplayName("환불 금액이 0이면 예외가 발생한다.")
        void createWithZeroAmount() {
            assertThatThrownBy(() -> PaymentRefund.create(1L, 0L, "고객 변심"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.ZERO_REFUND_AMOUNT);
        }

        @Test
        @DisplayName("환불 금액이 음수이면 예외가 발생한다.")
        void createWithNegativeAmount() {
            assertThatThrownBy(() -> PaymentRefund.create(1L, -1000L, "고객 변심"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.ZERO_REFUND_AMOUNT);
        }
    }
}