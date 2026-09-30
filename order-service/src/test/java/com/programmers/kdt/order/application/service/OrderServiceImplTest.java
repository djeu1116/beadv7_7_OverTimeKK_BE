package com.programmers.kdt.order.application.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.order.infrastructure.client.TicketClient;
import com.programmers.kdt.order.infrastructure.client.TicketInfo;
import com.programmers.kdt.order.presentation.dto.CancelOrderRequest;
import com.programmers.kdt.order.presentation.dto.CancelOrderResponse;
import com.programmers.kdt.order.presentation.dto.CreateOrderRequest;
import com.programmers.kdt.order.presentation.dto.CreateOrderResponse;
import com.programmers.kdt.order.presentation.dto.GetOrderHistoryResponse;
import com.programmers.kdt.order.presentation.dto.OrderTicketRequest;
import com.programmers.kdt.order.presentation.dto.TicketReserveRequest;
import com.programmers.kdt.order.presentation.dto.ValidateTicketRequest;
import com.programmers.kdt.order.domain.entity.Order;
import com.programmers.kdt.order.domain.entity.OrderItem;
import com.programmers.kdt.order.domain.entity.OrderStatus;
import com.programmers.kdt.common.contract.OrderCancelRequestedEvent;
import com.programmers.kdt.order.domain.entity.TicketCancelJob;
import com.programmers.kdt.order.domain.entity.outbox.OrderOutboxEventType;
import com.programmers.kdt.order.application.service.OrderOutboxEventWriter;
import com.programmers.kdt.order.domain.event.TicketCancelRequestEvent;
import com.programmers.kdt.order.domain.event.TicketReleaseRequestEvent;
import com.programmers.kdt.order.domain.exception.OrderErrorCode;
import com.programmers.kdt.order.infrastructure.repository.OrderRepository;
import com.programmers.kdt.order.infrastructure.repository.TicketCancelJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long TICKET_ID = 100L;
    private static final Long PRICE = 50_000L;
    private static final String HOLD_KEY = "hold-key";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TicketCancelJobRepository ticketCancelJobRepository;
    @Mock
    private TicketClient ticketClient;
    @Mock
    private OrderOutboxEventWriter orderOutboxEventWriter;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                orderRepository,
                ticketCancelJobRepository,
                ticketClient,
                eventPublisher,
                orderOutboxEventWriter
        );
    }

    @Nested
    @DisplayName("주문 생성")
    class CreateOrder {

        @Test
        @DisplayName("티켓을 검증하고 PENDING 상태의 주문을 저장한다")
        void success() {
            LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(10);
            CreateOrderRequest request = new CreateOrderRequest(
                    TICKET_ID, PRICE, expiredAt, HOLD_KEY
            );
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                ReflectionTestUtils.setField(order, "orderId", ORDER_ID);
                return order;
            });

            CreateOrderResponse response = orderService.createOrder(request, USER_ID);

            assertThat(response.orderId()).isEqualTo(ORDER_ID);
            assertThat(response.orderStatus()).isEqualTo(OrderStatus.PENDING.name());

            ArgumentCaptor<ValidateTicketRequest> validateCaptor =
                    ArgumentCaptor.forClass(ValidateTicketRequest.class);
            verify(ticketClient).validateTicket(validateCaptor.capture());
            assertThat(validateCaptor.getValue()).isEqualTo(
                    new ValidateTicketRequest(TICKET_ID, USER_ID, PRICE, HOLD_KEY)
            );

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            Order savedOrder = orderCaptor.getValue();
            assertThat(savedOrder.getUserId()).isEqualTo(USER_ID);
            assertThat(savedOrder.getTotalAmount()).isEqualTo(PRICE);
            assertThat(savedOrder.getExpiresAt()).isEqualTo(expiredAt);
            assertThat(savedOrder.getItems()).singleElement().satisfies(item -> {
                assertThat(item.getTicketId()).isEqualTo(TICKET_ID);
                assertThat(item.getHoldKey()).isEqualTo(HOLD_KEY);
            });
        }

        @Test
        @DisplayName("티켓 검증에 실패하면 주문을 저장하지 않는다")
        void ticketValidationFailure() {
            CreateOrderRequest request = new CreateOrderRequest(
                    TICKET_ID, PRICE, LocalDateTime.now().plusMinutes(10), HOLD_KEY
            );
            BusinessException exception = new BusinessException(OrderErrorCode.TICKET_VALIDATION_FAILED);
            org.mockito.Mockito.doThrow(exception)
                    .when(ticketClient).validateTicket(any(ValidateTicketRequest.class));

            assertThatThrownBy(() -> orderService.createOrder(request, USER_ID)).isSameAs(exception);

            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("결제 전 주문을 취소하면 좌석 해제 이벤트를 발행한다")
    void cancelPendingOrder() {
        Order order = pendingOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        CancelOrderResponse response = orderService.cancelPendingOrder(ORDER_ID, USER_ID);

        assertThat(response.orderStatus()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        ArgumentCaptor<TicketReleaseRequestEvent> captor =
                ArgumentCaptor.forClass(TicketReleaseRequestEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(
                new TicketReleaseRequestEvent(ORDER_ID, TICKET_ID, HOLD_KEY)
        );
    }

    @Nested
    @DisplayName("주문 완료")
    class CompleteOrder {

        @Test
        @DisplayName("결제 시작 주문의 티켓을 예약하고 주문을 완료한다")
        void success() {
            Order order = paymentStartedOrder();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.completeOrder(ORDER_ID);

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
            verify(ticketClient).reserveTicket(
                    new TicketReserveRequest(TICKET_ID, HOLD_KEY, USER_ID)
            );
        }

        @Test
        @DisplayName("이미 완료된 주문이면 티켓 예약을 다시 요청하지 않는다")
        void alreadyCompleted() {
            Order order = completedOrder();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.completeOrder(ORDER_ID);

            verify(ticketClient, never()).reserveTicket(any());
        }
    }

    @Nested
    @DisplayName("주문 만료")
    class ExpireOrder {

        @Test
        @DisplayName("조건부 만료에 성공하면 좌석 해제 이벤트를 발행한다")
        void success() {
            LocalDateTime now = LocalDateTime.now();
            Order order = pendingOrder();
            when(orderRepository.tryExpire(
                    ORDER_ID, OrderStatus.PENDING, OrderStatus.EXPIRED, now
            )).thenReturn(1);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.expireOrder(ORDER_ID, now);

            verify(eventPublisher).publishEvent(
                    new TicketReleaseRequestEvent(ORDER_ID, TICKET_ID, HOLD_KEY)
            );
        }

        @Test
        @DisplayName("다른 상태 전이가 먼저 성공하면 좌석 해제 이벤트를 발행하지 않는다")
        void transitionAlreadyWon() {
            LocalDateTime now = LocalDateTime.now();
            when(orderRepository.tryExpire(
                    ORDER_ID, OrderStatus.PENDING, OrderStatus.EXPIRED, now
            )).thenReturn(0);

            orderService.expireOrder(ORDER_ID, now);

            verify(orderRepository, never()).findById(anyLong());
            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("결제 완료 주문 취소")
    class CancelCompletedOrder {
      
        @Test
        @DisplayName("취소 접수 직후 CANCEL_REQUESTED 상태가 되고 티켓 취소 이벤트를 발행하지 않는다")
        void requestCancelDoesNotCancelTicketImmediately() {
            Order order = completedOrder();
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            CancelOrderResponse response = orderService.cancelCompletedOrder(
                ORDER_ID, USER_ID, new CancelOrderRequest("단순 변심")
            );

            assertThat(response.orderStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED.name());
            verify(orderOutboxEventWriter).enqueue(
                    org.mockito.ArgumentMatchers.eq(OrderOutboxEventType.ORDER_CANCEL_REQUESTED),
                    org.mockito.ArgumentMatchers.eq(ORDER_ID),
                    org.mockito.ArgumentMatchers.eq(new OrderCancelRequestedEvent(ORDER_ID, "단순 변심"))
            );
            verify(eventPublisher, never()).publishEvent(
                    any(TicketCancelRequestEvent.class)
            );
        }

        @Test
        @DisplayName("취소 접수 중 재요청하면 환불을 추가 접수하지 않는다")
        void duplicateRequestIsIdempotent() {
            Order order = completedOrder();
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            CancelOrderResponse firstResponse = orderService.cancelCompletedOrder(
                    ORDER_ID, USER_ID, new CancelOrderRequest("단순 변심")
            );
            CancelOrderResponse secondResponse = orderService.cancelCompletedOrder(
                    ORDER_ID, USER_ID, new CancelOrderRequest("단순 변심")
            );

            assertThat(firstResponse.orderStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED.name());
            assertThat(secondResponse).isEqualTo(firstResponse);
            verify(orderOutboxEventWriter, org.mockito.Mockito.times(1)).enqueue(
                    org.mockito.ArgumentMatchers.eq(OrderOutboxEventType.ORDER_CANCEL_REQUESTED),
                    org.mockito.ArgumentMatchers.eq(ORDER_ID),
                    any(OrderCancelRequestedEvent.class)
            );
            verify(eventPublisher, never()).publishEvent(
                    any(TicketCancelRequestEvent.class)
            );
        }
    }

    @Nested
    @DisplayName("환불 결과 반영")
    class HandleRefundResult {

        @Test
        @DisplayName("환불 성공 시 주문을 CANCELLED로 확정하고 티켓 취소 이벤트를 발행한다")
        void confirmCancellation() {
            Order order = cancelRequestedOrder();
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.confirmCancellation(ORDER_ID);

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
            ArgumentCaptor<TicketCancelJob> jobCaptor = ArgumentCaptor.forClass(TicketCancelJob.class);
            verify(ticketCancelJobRepository).save(jobCaptor.capture());
            assertThat(jobCaptor.getValue().getOrderId()).isEqualTo(ORDER_ID);
            assertThat(jobCaptor.getValue().getTicketId()).isEqualTo(TICKET_ID);
            assertThat(jobCaptor.getValue().getUserId()).isEqualTo(USER_ID);
            verify(eventPublisher).publishEvent(
                    new TicketCancelRequestEvent(TICKET_ID, USER_ID, ORDER_ID)
            );
        }

        @Test
        @DisplayName("환불 성공 이벤트를 중복 처리해도 티켓 취소 이벤트는 한 번만 발행한다")
        void duplicateConfirmCancellation() {
            Order order = cancelRequestedOrder();
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.confirmCancellation(ORDER_ID);
            orderService.confirmCancellation(ORDER_ID);

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(eventPublisher, org.mockito.Mockito.times(1)).publishEvent(
                    new TicketCancelRequestEvent(TICKET_ID, USER_ID, ORDER_ID)
            );
            verify(ticketCancelJobRepository, org.mockito.Mockito.times(1)).save(any(TicketCancelJob.class));
        }

        @Test
        @DisplayName("환불 실패 시 주문을 COMPLETED로 복구하고 티켓 취소 이벤트를 발행하지 않는다")
        void revertCancellation() {
            Order order = cancelRequestedOrder();
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.revertCancellation(ORDER_ID);

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
            verify(eventPublisher, never()).publishEvent(
                    any(TicketCancelRequestEvent.class)
            );
        }

        @Test
        @DisplayName("환불 실패 이벤트를 중복 처리해도 COMPLETED 상태를 유지한다")
        void duplicateRevertCancellation() {
            Order order = cancelRequestedOrder();
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.revertCancellation(ORDER_ID);
            orderService.revertCancellation(ORDER_ID);

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.COMPLETED);
            verify(eventPublisher, never()).publishEvent(
                    any(TicketCancelRequestEvent.class)
            );
        }
    }

    @Nested
    @DisplayName("보상 완료 처리")
    class HandleCompensation {

        @Test
        @DisplayName("보상 완료 시 PAYMENT_STARTED 주문을 CANCELLED로 종료하고 좌석 hold 해제 이벤트를 발행한다")
        void failOrderAfterCompensation() {
            Order order = paymentStartedOrder();
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.failOrderAfterCompensation(ORDER_ID);

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(eventPublisher).publishEvent(
                    new TicketReleaseRequestEvent(ORDER_ID, TICKET_ID, HOLD_KEY)
            );
            verifyNoInteractions(ticketCancelJobRepository);
        }

        @Test
        @DisplayName("보상 완료 이벤트를 중복 처리해도 좌석 해제 이벤트는 한 번만 발행한다")
        void duplicateFailOrderAfterCompensation() {
            Order order = paymentStartedOrder();
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.failOrderAfterCompensation(ORDER_ID);
            orderService.failOrderAfterCompensation(ORDER_ID);

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(eventPublisher, times(1)).publishEvent(
                    new TicketReleaseRequestEvent(ORDER_ID, TICKET_ID, HOLD_KEY)
            );
        }
    }

    @Nested
    @DisplayName("주문 내역 조회")
    class GetOrderHistory {

        @Test
        @DisplayName("완료/취소/만료 주문과 티켓 정보를 조합해 최신 주문 내역을 반환한다")
        void success() {
            LocalDateTime createdAt = LocalDateTime.of(2026, 8, 11, 12, 0);
            Order completed = completedOrder();
            Order cancelled = cancelledOrder();
            Order expired = expiredOrder();
            ReflectionTestUtils.setField(completed, "createdAt", createdAt);
            when(orderRepository.findByUserIdAndOrderStatusInOrderByCreatedAtDesc(
                    USER_ID, List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED, OrderStatus.EXPIRED)
            )).thenReturn(List.of(completed, cancelled, expired));
            when(ticketClient.getTickets(new OrderTicketRequest(List.of(TICKET_ID, 200L, 300L))))
                    .thenReturn(List.of(
                            new TicketInfo(TICKET_ID, "오버타임 콘서트", "VIP"),
                            new TicketInfo(200L, "오버타임 콘서트", "R"),
                            new TicketInfo(300L, "오버타임 콘서트", "A")
                    ));

            List<GetOrderHistoryResponse> responses = orderService.getOrderHistory(USER_ID);

            assertThat(responses).hasSize(3);
            assertThat(responses.getFirst()).satisfies(response -> {
                assertThat(response.orderId()).isEqualTo(ORDER_ID);
                assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED.name());
                assertThat(response.performanceName()).isEqualTo("오버타임 콘서트");
                assertThat(response.orderedAt()).isEqualTo(createdAt);
                assertThat(response.zone()).isEqualTo("VIP");
                assertThat(response.quantity()).isEqualTo(1);
                assertThat(response.totalAmount()).isEqualTo(PRICE);
            });
            assertThat(responses).extracting(GetOrderHistoryResponse::orderStatus)
                    .containsExactly(
                            OrderStatus.COMPLETED.name(),
                            OrderStatus.CANCELLED.name(),
                            OrderStatus.EXPIRED.name()
                    );
        }

        @Test
        @DisplayName("조회 대상 주문이 없으면 티켓 정보를 조회하지 않고 빈 목록을 반환한다")
        void empty() {
            when(orderRepository.findByUserIdAndOrderStatusInOrderByCreatedAtDesc(
                    USER_ID, List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED, OrderStatus.EXPIRED)
            )).thenReturn(List.of());

            assertThat(orderService.getOrderHistory(USER_ID)).isEmpty();

            verifyNoInteractions(ticketClient);
        }

        @Test
        @DisplayName("주문에 대응하는 티켓 정보가 없으면 예외가 발생한다")
        void ticketInfoNotFound() {
            Order order = completedOrder();
            when(orderRepository.findByUserIdAndOrderStatusInOrderByCreatedAtDesc(
                    USER_ID, List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED, OrderStatus.EXPIRED)
            )).thenReturn(List.of(order));
            when(ticketClient.getTickets(new OrderTicketRequest(List.of(TICKET_ID)))).thenReturn(List.of());

            assertBusinessException(
                    () -> orderService.getOrderHistory(USER_ID),
                    OrderErrorCode.TICKET_INFO_NOT_FOUND
            );
        }
    }

    @Test
    @DisplayName("결제 실패를 처리하면 PAYMENT_STARTED 주문이 PENDING으로 돌아간다")
    void handlePaymentFailed() {
        Order order = paymentStartedOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.handlePaymentFailed(ORDER_ID);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("주문이 존재하지 않으면 ORDER_NOT_FOUND 예외가 발생한다")
    void orderNotFound() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertBusinessException(
                () -> orderService.cancelPendingOrder(ORDER_ID, USER_ID),
                OrderErrorCode.ORDER_NOT_FOUND
        );
        verifyNoInteractions(eventPublisher);
    }

    private Order pendingOrder() {
        return pendingOrder(ORDER_ID, TICKET_ID, HOLD_KEY);
    }

    private Order pendingOrder(Long orderId, Long ticketId, String holdKey) {
        Order order = Order.create(
                USER_ID,
                List.of(OrderItem.create(ticketId, PRICE, holdKey)),
                LocalDateTime.now().plusMinutes(10)
        );
        ReflectionTestUtils.setField(order, "orderId", orderId);
        return order;
    }

    private Order orderWithExpiration(LocalDateTime expiredAt) {
        Order order = Order.create(
                USER_ID,
                List.of(OrderItem.create(TICKET_ID, PRICE, HOLD_KEY)),
                expiredAt
        );
        ReflectionTestUtils.setField(order, "orderId", ORDER_ID);
        return order;
    }

    private Order paymentStartedOrder() {
        Order order = pendingOrder();
        order.startPayment(LocalDateTime.now());
        return order;
    }

    private Order completedOrder() {
        Order order = paymentStartedOrder();
        order.complete();
        return order;
    }

    private Order cancelRequestedOrder() {
        Order order = completedOrder();
        order.requestCancel();
        return order;
    }
  
    private Order cancelledOrder() {
        Order order = pendingOrder(2L, 200L, "cancel-hold-key");
        order.cancelPending();
        return order;
    }

    private Order expiredOrder() {
        Order order = pendingOrder(3L, 300L, "expire-hold-key");
        order.expire();
        return order;
    }

    private void assertBusinessException(Runnable action, OrderErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
