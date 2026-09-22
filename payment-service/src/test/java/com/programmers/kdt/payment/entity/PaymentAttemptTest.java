package com.programmers.kdt.payment.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAttemptTest {

    @Test
    @DisplayName("생성하면 READY 상태이고 전달한 값이 그대로 담긴다.")
    void create() {
        PaymentAttempt attempt = PaymentAttempt.create(1L, 0, "PG_ORDER_1", 3000L);

        assertThat(attempt.getPaymentId()).isEqualTo(1L);
        assertThat(attempt.getAttemptSeq()).isEqualTo(0);
        assertThat(attempt.getPgOrderId()).isEqualTo("PG_ORDER_1");
        assertThat(attempt.getUsedPoint()).isEqualTo(3000L);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.READY);
        assertThat(attempt.getPaymentKey()).isNull();
    }

    @Test
    @DisplayName("paymentKey를 부여하면 그 값으로 바뀐다 - Payment와 달리 다음 시도가 생겨도 이 값은 안 바뀐다.")
    void assignPaymentKey() {
        PaymentAttempt attempt = PaymentAttempt.create(1L, 0, "PG_ORDER_1", 0L);

        attempt.assignPaymentKey("PG_KEY_1");

        assertThat(attempt.getPaymentKey()).isEqualTo("PG_KEY_1");
    }

    @Test
    @DisplayName("markPending/markPaid/markFailed는 상태만 바꾼다.")
    void markStatus() {
        PaymentAttempt pendingAttempt = PaymentAttempt.create(1L, 0, "PG_ORDER_1", 0L);
        pendingAttempt.markPending();
        assertThat(pendingAttempt.getStatus()).isEqualTo(PaymentAttemptStatus.PENDING_VERIFICATION);

        PaymentAttempt paidAttempt = PaymentAttempt.create(1L, 0, "PG_ORDER_1", 0L);
        paidAttempt.markPaid();
        assertThat(paidAttempt.getStatus()).isEqualTo(PaymentAttemptStatus.PAID);

        PaymentAttempt failedAttempt = PaymentAttempt.create(1L, 0, "PG_ORDER_1", 0L);
        failedAttempt.markFailed();
        assertThat(failedAttempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
    }
}
