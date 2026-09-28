package com.programmers.kdt.payment.client.pg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgResponseValidatorTest {

    @Test
    @DisplayName("orderId와 금액이 모두 기대값과 같으면 예외가 나지 않는다.")
    void bothMatch_doesNotThrow() {
        assertThatCode(() -> PgResponseValidator.validate("PG_KEY", "ORDER_1", 10000L, "ORDER_1", 10000L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("orderId가 다르면 PgResponseMismatchException이 발생한다.")
    void orderIdMismatch_throws() {
        assertThatThrownBy(() -> PgResponseValidator.validate("PG_KEY", "OTHER_ORDER", 10000L, "ORDER_1", 10000L))
                .isInstanceOf(PgResponseMismatchException.class);
    }

    @Test
    @DisplayName("금액이 다르면 PgResponseMismatchException이 발생한다.")
    void amountMismatch_throws() {
        assertThatThrownBy(() -> PgResponseValidator.validate("PG_KEY", "ORDER_1", 9999L, "ORDER_1", 10000L))
                .isInstanceOf(PgResponseMismatchException.class);
    }

    @Test
    @DisplayName("orderId와 금액이 둘 다 다르면 PgResponseMismatchException이 발생한다.")
    void bothMismatch_throws() {
        assertThatThrownBy(() -> PgResponseValidator.validate("PG_KEY", "OTHER_ORDER", 9999L, "ORDER_1", 10000L))
                .isInstanceOf(PgResponseMismatchException.class);
    }
}
