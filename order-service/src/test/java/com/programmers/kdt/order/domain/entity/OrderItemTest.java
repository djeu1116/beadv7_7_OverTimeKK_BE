package com.programmers.kdt.order.domain.entity;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.order.domain.exception.OrderErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    @Nested
    @DisplayName("주문 항목 생성")
    class CreateOrderItem {
        private static final String HOLD_KEY = "test-hold-key";

        @Test
        @DisplayName("정상적인 ticketId와 ticketPrice로 주문 항목을 생성한다.")
        void createOrderItem() {
            // given
            Long ticketId = 1L;
            Long ticketPrice = 50_000L;

            // when
            OrderItem orderItem = OrderItem.create(ticketId, ticketPrice, HOLD_KEY);

            // then
            assertThat(orderItem.getTicketId()).isEqualTo(ticketId);
            assertThat(orderItem.getTicketPrice()).isEqualTo(ticketPrice);
        }

        @Test
        @DisplayName("티켓 가격이 null이면 예외가 발생한다.")
        void failWhenPriceIsNull() {
            assertThatThrownBy(() -> OrderItem.create(1L, null, HOLD_KEY))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.INVALID_ORDER_AMOUNT)
                    );
        }

        @Test
        @DisplayName("티켓 가격이 0이면 예외가 발생한다.")
        void failWhenPriceIsZero() {
            assertThatThrownBy(() -> OrderItem.create(1L, 0L, HOLD_KEY))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.INVALID_ORDER_AMOUNT)
                    );
        }

        @Test
        @DisplayName("티켓 가격이 음수이면 예외가 발생한다.")
        void failWhenPriceIsNegative() {
            assertThatThrownBy(() -> OrderItem.create(1L, -1_000L, HOLD_KEY))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.INVALID_ORDER_AMOUNT)
                    );
        }
    }
}