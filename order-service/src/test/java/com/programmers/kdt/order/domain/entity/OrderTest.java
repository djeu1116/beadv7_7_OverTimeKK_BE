package com.programmers.kdt.order.domain.entity;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.order.domain.exception.OrderErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OrderTest {

    private final LocalDateTime expiresAt =
            LocalDateTime.now().plusMinutes(10);
    private static final String HOLD_KEY = "test-hold-key";

    private List<OrderItem> createItems(){

        return List.of(
                OrderItem.create(10L, 50_000L, HOLD_KEY),
                OrderItem.create(20L, 70_000L, HOLD_KEY),
                OrderItem.create(30L, 100_000L, HOLD_KEY)
        );
    }

    @Nested
    @DisplayName("주문 생성")
    public class CreateOrder{

        @Test
        @DisplayName("정상적인 userId와 orderItem으로 생성하면 PENDING 상태로 초기화 된다.")
        void createOrder(){
            // given
            Long userId = 1L;
            List<OrderItem> items = createItems();

            // when
            Order order = Order.create(userId, items, expiresAt);

            // then
            assertThat(order.getUserId()).isEqualTo(userId);
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getTotalAmount()).isEqualTo(220_000L);
        }

        @Test
        @DisplayName("주문 항목 목록이 null이면 예외가 발생한다.")
        void createOrderNullOrderItem(){
            assertThatThrownBy(() -> Order.create(1L, null, expiresAt))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception ->{
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(OrderErrorCode.ORDER_ITEMS_REQUIRED);
                            }
                    );
        }

        @Test
        @DisplayName("주문 항목 목록이 비어있으면 예외가 발생한다.")
        void createOrderEmptyOrderItem(){
            assertThatThrownBy(() -> Order.create(1L, List.of(), expiresAt))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> {
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(OrderErrorCode.ORDER_ITEMS_REQUIRED);
                            }
                    );

        }

        @Test
        @DisplayName("주문 항목 목록에 null 항목이 포함되면 예외가 발생한다.")
        void createOrderWithNullItem() {
            // given
            List<OrderItem> items = new ArrayList<>();
            items.add(null);

            // when & then
            assertThatThrownBy(() -> Order.create(1L, items, expiresAt))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> {
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(OrderErrorCode.ORDER_ITEMS_REQUIRED);
                            }
                    );
        }
    }

    @Nested
    @DisplayName("주문 완료")
    class CompleteOrder{

        @Test
        @DisplayName("PAYMENT_STARTED 상태에서 주문 완료되면 COMPLETED 상태로 전이된다.")
        void completeOrder() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));

            // when
            order.complete();

            // then
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
        }

        @Test
        @DisplayName("이미 COMPLETED 상태면 예외 없이 무시해도 된다.")
        void ignoreAlreadyCompleted(){
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));
            order.complete();

            // when & then
            order.complete();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
        }

        @Test
        @DisplayName("EXPIRED 상태에서 완료하면 예외가 발생한다.")
        void failToCompleteExpiredOrder(){
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.expire();

            // when & then
            assertThatThrownBy(order::complete)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> {
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(OrderErrorCode.ORDER_NOT_PAYMENT_STARTED);
                            });

        }
    }

    @Nested
    @DisplayName("주문 만료")
    class ExpireOrder{

        @Test
        @DisplayName("PENDING 상태에서 만료 시간이 지나면 EXPIRED로 상태가 전이된다.")
        void expireOrder(){
            // given
            Order order = Order.create(1L, createItems(), expiresAt);

            // when
            order.expire();

            // then
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.EXPIRED);
        }

        @Test
        @DisplayName("이미 EXPIRED 상태면 예외 없이 무시해도 된다.")
        void ignoreAlreadyExpired(){
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.expire();

            // when
            order.expire();

            // then
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.EXPIRED);
        }

        @Test
        @DisplayName("COMPLETED 상태에서 만료하면 예외가 발생한다.")
        void failToExpireCompletedOrder() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));
            order.complete();

            // when & then
            assertThatThrownBy(order::expire)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_NOT_PENDING)
                    );
        }

        @Test
        @DisplayName("CANCELLED 상태에서 만료하면 예외가 발생한다.")
        void failToExpireCancelledOrder() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));
            order.complete();
            order.requestCancel();
            order.confirmCancel();

            // when & then
            assertThatThrownBy(order::expire)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_NOT_PENDING)
                    );
        }

    }

    @Nested
    @DisplayName("주문 취소")
    class CancelOrder {

        @Test
        @DisplayName("PENDING 상태에서 결제 전 취소하면 CANCELLED 상태로 전이된다.")
        void cancelPendingOrder() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);

            // when
            order.cancelPending();

            // then
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getCancelledAt()).isNotNull();
        }

        @Test
        @DisplayName("PENDING이 아닌 상태에서 결제 전 취소하면 예외가 발생한다.")
        void failToCancelPendingOrderWhenNotPending() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.expire();

            // when & then
            assertThatThrownBy(order::cancelPending)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_NOT_PENDING)
                    );
        }

        @Test
        @DisplayName("COMPLETED 상태에서 취소를 접수하면 CANCEL_REQUESTED 상태로 전이된다.")
        void requestCancel() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));
            order.complete();

            // when
            order.requestCancel();

            // then
            assertThat(order.getOrderStatus())
                    .isEqualTo(OrderStatus.CANCEL_REQUESTED);
        }

        @Test
        @DisplayName("환불 성공 시 CANCEL_REQUESTED 주문이 CANCELLED 상태로 전이된다.")
        void confirmCancel() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));
            order.complete();
            order.requestCancel();

            // when
            boolean transitioned = order.confirmCancel();

            // then
            assertThat(transitioned).isTrue();
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getCancelledAt()).isNotNull();
        }

        @Test
        @DisplayName("환불 실패 시 CANCEL_REQUESTED 주문이 COMPLETED 상태로 복구된다.")
        void revertCancel() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));
            order.complete();
            order.requestCancel();

            // when
            order.revertCancel();

            // then
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
        }

        @Test
        @DisplayName("PENDING 상태에서 취소하면 예외가 발생한다.")
        void failToCancelPendingOrder() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);

            // when & then
            assertThatThrownBy(order::requestCancel)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_NOT_COMPLETED)
                    );
        }

        @Test
        @DisplayName("EXPIRED 상태에서 취소하면 예외가 발생한다.")
        void failToCancelExpiredOrder() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.expire();

            // when & then
            assertThatThrownBy(order::requestCancel)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_NOT_COMPLETED)
                    );
        }

        @Test
        @DisplayName("PAYMENT_STARTED 상태에서는 취소를 접수할 수 없다.")
        void failToRequestCancelPaymentStartedOrder() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));

            // when & then
            assertThatThrownBy(order::requestCancel)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_NOT_COMPLETED)
                    );
        }

        @Test
        @DisplayName("CANCELLED 상태에서는 취소를 다시 접수할 수 없다.")
        void failToRequestCancelAlreadyCancelledOrder() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));
            order.complete();
            order.requestCancel();
            order.confirmCancel();

            // when & then
            assertThatThrownBy(order::requestCancel)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_ALREADY_CANCELLED)
                    );
        }

        @Test
        @DisplayName("CANCEL_REQUESTED 상태에서는 주문 완료 처리를 할 수 없다.")
        void failToCompleteCancelRequestedOrder() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));
            order.complete();
            order.requestCancel();

            // when & then
            assertThatThrownBy(order::complete)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_NOT_PAYMENT_STARTED)
                    );
        }

        @Test
        @DisplayName("취소 접수 상태가 아니면 취소 확정을 할 수 없다.")
        void failToConfirmCancelWhenNotRequested() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));
            order.complete();

            // when & then
            assertThatThrownBy(order::confirmCancel)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_CANCEL_NOT_REQUESTED)
                    );
        }

        @Test
        @DisplayName("취소 접수 상태가 아니면 취소를 복구할 수 없다.")
        void failToRevertCancelWhenNotRequested() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);

            // when & then
            assertThatThrownBy(order::revertCancel)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_CANCEL_NOT_REQUESTED)
                    );
        }

        @Test
        @DisplayName("중복 환불 성공 처리는 상태를 유지하고 false를 반환한다.")
        void ignoreDuplicateConfirmCancel() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));
            order.complete();
            order.requestCancel();
            order.confirmCancel();

            // when
            boolean transitioned = order.confirmCancel();

            // then
            assertThat(transitioned).isFalse();
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("중복 환불 실패 처리는 COMPLETED 상태를 유지한다.")
        void ignoreDuplicateRevertCancel() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));
            order.complete();
            order.requestCancel();
            order.revertCancel();

            // when
            order.revertCancel();

            // then
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("결제 시작")
    class StartPayment {

        @Test
        @DisplayName("만료 전 PENDING 주문은 PAYMENT_STARTED 상태로 전이된다.")
        void startPayment() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);

            // when
            order.startPayment(expiresAt.minusSeconds(1));

            // then
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAYMENT_STARTED);
        }

        @Test
        @DisplayName("만료 시각과 현재 시각이 같으면 예외가 발생한다.")
        void failToStartPaymentAtExpiration() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);

            // when & then
            assertThatThrownBy(() -> order.startPayment(expiresAt))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_ALREADY_EXPIRED)
                    );
        }

        @Test
        @DisplayName("만료 시각이 지난 주문이면 예외가 발생한다.")
        void failToStartPaymentAfterExpiration() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);

            // when & then
            assertThatThrownBy(() -> order.startPayment(expiresAt.plusSeconds(1)))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_ALREADY_EXPIRED)
                    );
        }

        @Test
        @DisplayName("PENDING이 아닌 주문에서 결제를 시작하면 예외가 발생한다.")
        void failToStartPaymentWhenNotPending() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.expire();

            // when & then
            assertThatThrownBy(() -> order.startPayment(expiresAt.minusSeconds(1)))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_NOT_PENDING)
                    );
        }
    }

    @Nested
    @DisplayName("결제 실패")
    class FailPayment {

        @Test
        @DisplayName("PAYMENT_STARTED 주문은 결제 실패 후 PENDING 상태로 돌아간다.")
        void failPayment() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));

            // when
            order.failPayment();

            // then
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("이미 PENDING 상태면 결제 실패 처리를 무시한다.")
        void ignoreWhenAlreadyPending() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);

            // when
            order.failPayment();

            // then
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("PAYMENT_STARTED와 PENDING이 아닌 상태면 예외가 발생한다.")
        void failToHandlePaymentFailureInInvalidStatus() {
            // given
            Order order = Order.create(1L, createItems(), expiresAt);
            order.expire();

            // when & then
            assertThatThrownBy(order::failPayment)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_NOT_PAYMENT_STARTED)
                    );
        }
    }

    @Nested
    @DisplayName("결제 후 후속 단계 영구 실패로 인한 보상 종료")
    public class FailAfterPayment {

        private Order paymentStartedOrder() {
            Order order = Order.create(1L, createItems(), expiresAt);
            order.startPayment(expiresAt.minusSeconds(1));
            return order;
        }

        @Test
        @DisplayName("PAYMENT_STARTED 주문이 CANCELLED로 전이되고 true를 반환한다.")
        void failAfterPayment() {
            Order order = paymentStartedOrder();

            boolean transitioned = order.failAfterPayment();

            assertThat(transitioned).isTrue();
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getCancelledAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 CANCELLED면 아무것도 하지 않고 false를 반환한다 - 중복 이벤트 수신 대비.")
        void alreadyCancelled_isNoOp() {
            Order order = paymentStartedOrder();
            order.failAfterPayment();

            boolean transitioned = order.failAfterPayment();

            assertThat(transitioned).isFalse();
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("PAYMENT_STARTED가 아닌 주문(예: PENDING)이면 예외가 발생한다.")
        void notPaymentStarted_throws() {
            Order order = Order.create(1L, createItems(), expiresAt);

            assertThatThrownBy(order::failAfterPayment)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_NOT_PAYMENT_STARTED)
                    );
        }
    }
}
