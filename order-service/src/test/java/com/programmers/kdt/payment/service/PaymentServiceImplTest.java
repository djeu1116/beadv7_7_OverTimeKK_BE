package com.programmers.kdt.payment.service;


import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.exception.CommonErrorCode;
import com.programmers.kdt.order.entity.Order;
import com.programmers.kdt.order.entity.OrderStatus;
import com.programmers.kdt.order.repository.OrderRepository;
import com.programmers.kdt.payment.client.pay.PaymentConfirmEvent;
import com.programmers.kdt.payment.client.pay.PaymentFailEvent;
import com.programmers.kdt.payment.client.pay.PaymentResultEventPublisher;
import com.programmers.kdt.payment.client.pg.*;
import com.programmers.kdt.payment.client.refund.*;
import com.programmers.kdt.payment.dto.*;
import com.programmers.kdt.payment.entity.Payment;
import com.programmers.kdt.payment.entity.PaymentRefund;
import com.programmers.kdt.payment.entity.PaymentStatus;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import com.programmers.kdt.payment.exception.PointErrorCode;
import com.programmers.kdt.payment.repository.PaymentRefundRepository;
import com.programmers.kdt.payment.repository.PaymentRepository;
import com.programmers.kdt.payment.service.tx.PaymentTxOps;
import com.programmers.kdt.payment.service.tx.PgOutcome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRefundRepository paymentRefundRepository;
    @Mock
    private PgClient pgClient;
    @Mock
    private OrderClient orderClient;
    @Mock
    private PerformanceClient performanceClient;
    @Mock
    private RefundEventPublisher refundEventPublisher;
    @Mock
    private PointService pointService;
    @Mock
    private PaymentResultEventPublisher paymentResultEventPublisher;
    @Mock
    private IdempotencyKeyService idempotencyKeyService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private PaymentTxOps paymentTxOps;

    private PaymentService paymentService;



    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRepository, orderRepository, paymentRefundRepository, refundEventPublisher, performanceClient, orderClient, pgClient, pointService, idempotencyKeyService, objectMapper, paymentResultEventPublisher, paymentTxOps);
        lenient().when(idempotencyKeyService.generate(any(String.class), any(String.class))).thenReturn(Optional.empty());
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        lenient().when(orderRepository.tryStartPayment(
                anyLong(), eq(OrderStatus.PENDING), eq(OrderStatus.PAYMENT_STARTED), any(LocalDateTime.class)
        )).thenReturn(1);
    }

    @Nested
    @DisplayName("결제 생성")
    class Pay {

        private final CreatePaymentRequest request = new CreatePaymentRequest(1L, 10000L, 0L);

        @Test
        @DisplayName("정상 요청이면 결제가 생성된다.")
        void successPayment() {
            Order order = mock(Order.class);
            when(order.getOrderId()).thenReturn(1L);
            when(order.getUserId()).thenReturn(1L);
            when(order.getTotalAmount()).thenReturn(10000L);

            PgReadyResult readyResult = mock(PgReadyResult.class);
            when(readyResult.transactionKey()).thenReturn("PG_KEY_123");
            when(readyResult.orderId()).thenReturn("PG_ORDER_1");
            when(readyResult.redirectionUrl()).thenReturn("https://pg.example/redirect");

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
            when(pgClient.ready(any())).thenReturn(readyResult);

            CreatePaymentResponse response = paymentService.pay("idem-key", request, 1L);

            assertThat(response.status()).isEqualTo(PaymentStatus.READY.name());
            assertThat(response.amount()).isEqualTo(10000L);
            assertThat(response.transactionKey()).isEqualTo("PG_KEY_123");

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            Payment saved = captor.getValue();
            assertThat(saved.getOrderId()).isEqualTo(1L);
            assertThat(saved.getUserId()).isEqualTo(1L);
            assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.READY);
            verify(orderRepository).tryStartPayment(
                    eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.PAYMENT_STARTED), any(LocalDateTime.class)
            );
        }

        @Test
        @DisplayName("만료 주문과의 조건부 상태 전이에 실패하면 PG를 호출하지 않는다")
        void expiredOrderDoesNotRequestPg() {
            Order order = mock(Order.class);
            when(order.getOrderId()).thenReturn(1L);
            when(order.getUserId()).thenReturn(1L);
            when(order.getExpiresAt()).thenReturn(LocalDateTime.now().minusSeconds(1));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
            when(orderRepository.tryStartPayment(
                    eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.PAYMENT_STARTED), any(LocalDateTime.class)
            )).thenReturn(0);

            assertThatThrownBy(() -> paymentService.pay("idem-key", request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.ORDER_ALREADY_EXPIRED);

            verifyNoInteractions(pgClient);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("만료 전이 외의 상태 변경으로 결제 시작에 실패해도 PG를 호출하지 않는다")
        void nonPendingOrderDoesNotRequestPg() {
            Order order = mock(Order.class);
            when(order.getOrderId()).thenReturn(1L);
            when(order.getUserId()).thenReturn(1L);
            when(order.getExpiresAt()).thenReturn(LocalDateTime.now().plusMinutes(1));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
            when(orderRepository.tryStartPayment(
                    eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.PAYMENT_STARTED), any(LocalDateTime.class)
            )).thenReturn(0);

            assertThatThrownBy(() -> paymentService.pay("idem-key", request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.ORDER_NOT_PENDING);

            verifyNoInteractions(pgClient);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("이전에 실패한 결제가 있으면 재시도로 처리된다.")
        void successPaymentRetryAfterFailed() {
            Order order = mock(Order.class);
            when(order.getUserId()).thenReturn(1L);
            when(order.getTotalAmount()).thenReturn(10000L);

            PgReadyResult readyResult = mock(PgReadyResult.class);
            when(readyResult.transactionKey()).thenReturn("PG_KEY_123");
            when(readyResult.orderId()).thenReturn("PG_ORDER_1");
            when(readyResult.redirectionUrl()).thenReturn("https://pg.example/redirect");

            Payment failedPayment = Payment.create(1L, 1L, 10000L);
            failedPayment.fail();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(failedPayment));
            when(pgClient.ready(any())).thenReturn(readyResult);

            paymentService.pay("idem-key", request, 1L);

            verify(paymentRepository).save(failedPayment);
            assertThat(failedPayment.getOrderId()).isEqualTo(1L);
            assertThat(failedPayment.getUserId()).isEqualTo(1L);
            assertThat(failedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.READY);
            assertThat(failedPayment.getPgOrderId()).isEqualTo("PG_ORDER_1");
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 예외가 발생한다.")
        void payOrderNotFound() {

            when(orderRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.pay("idem-key", request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.ORDER_NOT_FOUND);

            verifyNoInteractions(pgClient);
        }

        @Test
        @DisplayName("이미 결제가 생성된 주문이면 예외가 발생한다.")
        void payAlreadyExists() {
            Order order = mock(Order.class);
            when(order.getUserId()).thenReturn(1L);

            Payment existingPayment = Payment.create(1L, 1L, 10000L);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(existingPayment));

            assertThatThrownBy(() -> paymentService.pay("idem-key", request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_EXISTS);

            verifyNoInteractions(pgClient);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("요청 금액이 주문 금액과 다르면 예외가 발생한다.")
        void payAmountMismatch() {
            Order order = mock(Order.class);
            when(order.getUserId()).thenReturn(1L);
            when(order.getTotalAmount()).thenReturn(15000L);


            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.pay("idem-key", request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_AMOUNT);

            verifyNoInteractions(pgClient);
            verify(paymentRepository, never()).save(any());

        }

        @Test
        @DisplayName("PG사 요청이 실패하면 예외가 발생하고 저장되지 않는다.")
        void payPgFailure() {
            Order order = mock(Order.class);
            when(order.getUserId()).thenReturn(1L);
            when(order.getTotalAmount()).thenReturn(10000L);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
            when(pgClient.ready(any())).thenThrow(new PgClientException("PG_ERR_001", "카드 승인 거절"));

            assertThatThrownBy(() -> paymentService.pay("idem-key", request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PG_REQUEST_FAILED);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("포인트를 사용하면 PG 요청 금액이 차감되고 pointService.usePoint가 호출된다.")
        void successPaymentWithPoint() {
            Order order = mock(Order.class);
            when(order.getOrderId()).thenReturn(1L);
            when(order.getUserId()).thenReturn(1L);
            when(order.getTotalAmount()).thenReturn(10000L);

            PgReadyResult readyResult = mock(PgReadyResult.class);
            when(readyResult.transactionKey()).thenReturn("PG_KEY_123");
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

            when(pgClient.ready(any())).thenReturn(readyResult);

            CreatePaymentRequest pointRequest = new CreatePaymentRequest(1L, 10000L, 3000L);
            CreatePaymentResponse response = paymentService.pay("idem-key", pointRequest, 1L);

            assertThat(response.status()).isEqualTo(PaymentStatus.READY.name());
            assertThat(response.amount()).isEqualTo(10000L);

            verify(pointService).usePoint(1L, 3000L, "ORDER:1:POINT_USE");

            ArgumentCaptor<PgReadyCommand> captor = ArgumentCaptor.forClass(PgReadyCommand.class);
            verify(pgClient).ready(captor.capture());
            assertThat(captor.getValue().amount()).isEqualTo(7000L);
        }

        @Test
        @DisplayName("사용 포인트가 없으면 pointService는 호출되지 않는다.")
        void successPaymentWithoutPoint() {
            Order order = Mockito.mock(Order.class);
            when(order.getOrderId()).thenReturn(1L);
            when(order.getUserId()).thenReturn(1L);
            when(order.getTotalAmount()).thenReturn(10000L);

            PgReadyResult readyResult = mock(PgReadyResult.class);
            when(readyResult.transactionKey()).thenReturn("PG_KEY_123");

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
            when(pgClient.ready(any())).thenReturn(readyResult);

            paymentService.pay("idem-key", request, 1L);

            verifyNoInteractions(pointService);
        }

        @Test
        @DisplayName("사용 포인트가 음수면 예외가 발생한다.")
        void payUsedPointNegative() {
            Order order = Mockito.mock(Order.class);
            when(order.getUserId()).thenReturn(1L);
            when(order.getTotalAmount()).thenReturn(10000L);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

            CreatePaymentRequest invalidRequest = new CreatePaymentRequest(1L, 10000L, -100L);

            assertThatThrownBy(() -> paymentService.pay("idem-key", invalidRequest, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_AMOUNT);

            verifyNoInteractions(pgClient, pointService);
        }

        @Test
        @DisplayName("사용 포인트가 주문 금액보다 크면 예외가 발생한다.")
        void payUsedPointExceedsAmount() {
            Order order = Mockito.mock(Order.class);
            when(order.getUserId()).thenReturn(1L);
            when(order.getTotalAmount()).thenReturn(10000L);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

            CreatePaymentRequest invalidRequest = new CreatePaymentRequest(1L, 10000L, 15000L);

            assertThatThrownBy(() -> paymentService.pay("idem-key", invalidRequest, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_AMOUNT);

            verifyNoInteractions(pgClient, pointService);

        }
    }

    @Nested
    @DisplayName("결제 승인")
    class Confirm {

        private Payment payment;

        @BeforeEach
        void setUp() {
            payment = Payment.create(1L, 100L, 10000L);
            payment.assignPaymentKey("PG_KEY_123");
            ReflectionTestUtils.setField(payment, "id", 1L);
        }

        @Test
        @DisplayName("PG 승인이 성공하면 결제 상태가 PAID로 바뀐다.")
        void confirmSuccess() {
            when(paymentTxOps.assignKeyAndCommit(eq(1L), any()))
                    .thenAnswer(inv -> {
                        payment.markPending();
                        return new PaymentTxOps.ReadyPaymentContext(payment, 0L);
                    });
            when(paymentTxOps.applyConfirmResult(eq(1L), eq(PgOutcome.SUCCESS)))
                    .thenAnswer(inv -> {
                        payment.confirmVerifiedSuccess();
                        return payment;
                    });


            PgApproveResult approveResult = mock(PgApproveResult.class);
            when(approveResult.success()).thenReturn(true);
            when(pgClient.approve(any())).thenReturn(approveResult);

            ConfirmPaymentResponse response = paymentService.confirm(1L, new ConfirmPaymentRequest("PG_KEY_123"), "idem-key", 100L);

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(payment.getPaymentKey()).isEqualTo("PG_KEY_123");
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("PG 승인이 실패하면 결제 상태가 FAILED로 바뀐다.")
        void confirmFailure() {
            when(paymentTxOps.assignKeyAndCommit(eq(1L), any()))
                    .thenAnswer(inv -> {
                        payment.markPending();
                        return new PaymentTxOps.ReadyPaymentContext(payment, 0L);
                    });
            when(paymentTxOps.applyConfirmResult(eq(1L), eq(PgOutcome.EXPLICIT_FAIL)))
                    .thenAnswer(inv -> {
                        payment.confirmVerifiedFail();
                        return payment;
                    });


            PgApproveResult approveResult = mock(PgApproveResult.class);
            when(approveResult.success()).thenReturn(false);
            when(pgClient.approve(any())).thenReturn(approveResult);

            paymentService.confirm(1L, new ConfirmPaymentRequest("PG_KEY_123"), "idem-key", 100L);

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);

            ArgumentCaptor<PaymentFailEvent> captor = ArgumentCaptor.forClass(PaymentFailEvent.class);
            verify(paymentResultEventPublisher).publishFailed(captor.capture());
            assertThat(captor.getValue().orderId()).isEqualTo(payment.getOrderId());
            assertThat(captor.getValue().paymentId()).isEqualTo(payment.getId());
        }

        @Test
        @DisplayName("결제를 찾을 수 없으면 예외가 발생한다.")
        void confirmPaymentNotFound() {
            when(paymentTxOps.assignKeyAndCommit(eq(1L), any()))
                    .thenThrow(new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

            assertThatThrownBy(() -> paymentService.confirm(1L, new ConfirmPaymentRequest("PG_KEY_123"), "idem-key", 100L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);

            verifyNoInteractions(pgClient);
            verifyNoInteractions(paymentResultEventPublisher);

        }

        @Test
        @DisplayName("READY 상태가 아니면 예외가 발생하고 PG 승인 요청은 나가지 않는다.")
        void confirmInvalidStatus() {
            when(paymentTxOps.assignKeyAndCommit(eq(1L), any()))
                    .thenThrow(new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS, PaymentStatus.PAID));

            assertThatThrownBy(() -> paymentService.confirm(1L, new ConfirmPaymentRequest("PG_KEY_123"), "idem-key", 100L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);

            verifyNoInteractions(pgClient);
            verifyNoInteractions(paymentResultEventPublisher);
        }

        @Test
        @DisplayName("PG사가 승인을 거절하면 결제 상태가 FAILED로 바뀐다.")
        void confirmPgClientExceptionMarksFailed() {
            when(paymentTxOps.assignKeyAndCommit(eq(1L), any()))
                    .thenAnswer(inv -> {
                        payment.markPending();
                        return new PaymentTxOps.ReadyPaymentContext(payment, 0L);
                    });
            when(paymentTxOps.applyConfirmResult(eq(1L), eq(PgOutcome.EXPLICIT_FAIL)))
                    .thenAnswer(inv -> {
                        payment.confirmVerifiedFail();
                        return payment;
                    });
            when(pgClient.approve(any())).thenThrow(new PgClientException("PG_DENIED", "거절"));

            paymentService.confirm(1L, new ConfirmPaymentRequest("PG_KEY_123"), "idem-key", 100L);

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(paymentResultEventPublisher).publishFailed(any());

        }

        @Test
        @DisplayName("PG 응답이 없으면(타임 아웃) 예외 없이 재조회 대상 상태로 남는다.")
        void confirmPgTimeoutStaysAmbiguousForReconciliation() {
            when(paymentTxOps.assignKeyAndCommit(eq(1L), any()))
                    .thenAnswer(inv -> {
                        payment.markPending();
                        return new PaymentTxOps.ReadyPaymentContext(payment, 0L);
                    });
            when(paymentTxOps.applyConfirmResult(eq(1L), eq(PgOutcome.AMBIGUOUS))).thenReturn(payment);
            when(paymentTxOps.applyReconcileResult(eq(1L), eq(PgOutcome.AMBIGUOUS))).thenReturn(payment);
            when(pgClient.approve(any())).thenThrow(new RestClientException("timeout"));

            ConfirmPaymentResponse response = paymentService.confirm(1L, new ConfirmPaymentRequest("PG_KEY_123"), "idem-key", 100L);

            assertThat(response).isNotNull();
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRM_PENDING_VERIFICATION);

            verifyNoInteractions(paymentResultEventPublisher);
            verify(paymentTxOps).applyReconcileResult(eq(1L), eq(PgOutcome.AMBIGUOUS));
        }

        @Test
        @DisplayName("PG 승인 호출에서 처리되지 않는 예외가 발생하면 tx2를 타지 않고 예외가 그대로 전파되며, 결제는 재조회 대상 상태로 남는다.")
        void confirmUnexpectedExceptionSkipsTx2AndPropagates() {
            when(paymentTxOps.assignKeyAndCommit(eq(1L), any()))
                    .thenAnswer(inv -> { payment.markPending(); return new PaymentTxOps.ReadyPaymentContext(payment, 0L); });
            when(pgClient.approve(any())).thenThrow(new NullPointerException("PG 응답 파싱 실패"));

            assertThatThrownBy(() -> paymentService.confirm(1L, new ConfirmPaymentRequest("PG_KEY_123"), "idem-key", 100L))
                    .isInstanceOf(NullPointerException.class);

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRM_PENDING_VERIFICATION);
            verify(paymentTxOps, never()).applyConfirmResult(anyLong(), any());
            verifyNoInteractions(paymentResultEventPublisher);
            verify(idempotencyKeyService).release("CONFIRM:idem-key");
        }

        @Test
        @DisplayName("포인트를 사용한 결제가 승인되면 PG 승인 금액에서 포인트만큼 차감되고, 포인트는 다시 건드리지 않는다.")
        void confirmSuccessWithPoint() {
            when(paymentTxOps.assignKeyAndCommit(eq(1L), any()))
                    .thenAnswer(inv -> {
                        payment.markPending();
                        return new PaymentTxOps.ReadyPaymentContext(payment, 3000L);
                    });
            when(paymentTxOps.applyConfirmResult(eq(1L), eq(PgOutcome.SUCCESS)))
                    .thenAnswer(inv -> {
                        payment.confirmVerifiedSuccess();
                        return payment;
                    });

            PgApproveResult approveResult = mock(PgApproveResult.class);
            when(approveResult.success()).thenReturn(true);
            when(pgClient.approve(any())).thenReturn(approveResult);

            paymentService.confirm(1L, new ConfirmPaymentRequest("PG_KEY_123"), "idem-key", 100L);

            ArgumentCaptor<PgApproveCommand> captor = ArgumentCaptor.forClass(PgApproveCommand.class);
            verify(pgClient).approve(captor.capture());
            assertThat(captor.getValue().amount()).isEqualTo(7000L);

            verify(pointService, never()).rollbackPoint(any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("포인트를 사용한 결제의 승인이 실패하면 사용했던 포인트만큼 롤백된다.")
        void confirmFailureWithPoint() {
            when(paymentTxOps.assignKeyAndCommit(eq(1L), any()))
                    .thenAnswer(inv -> { payment.markPending(); return new PaymentTxOps.ReadyPaymentContext(payment, 3000L); });
            when(paymentTxOps.applyConfirmResult(eq(1L), eq(PgOutcome.EXPLICIT_FAIL)))
                    .thenAnswer(inv -> { payment.confirmVerifiedFail(); return payment; });

            PgApproveResult approveResult = mock(PgApproveResult.class);
            when(approveResult.success()).thenReturn(false);
            when(pgClient.approve(any())).thenReturn(approveResult);

            paymentService.confirm(1L, new ConfirmPaymentRequest("PG_KEY_123"), "idem-key", 100L);

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(pointService).rollbackPoint("ORDER:1:POINT_USE", 3000L, "ORDER:1:POINT_ROLLBACK_FAIL", true);
        }

        @Test
        @DisplayName("포인트를 사용하지 않은 결제의 승인이 실패하면 포인트 롤백은 호출되지 않는다.")
        void confirmFailureWithoutPoint() {
            when(paymentTxOps.assignKeyAndCommit(eq(1L), any()))
                    .thenAnswer(inv -> { payment.markPending(); return new PaymentTxOps.ReadyPaymentContext(payment, 0L); });
            when(paymentTxOps.applyConfirmResult(eq(1L), eq(PgOutcome.EXPLICIT_FAIL)))
                    .thenAnswer(inv -> { payment.confirmVerifiedFail(); return payment; });

            PgApproveResult approveResult = mock(PgApproveResult.class);
            when(approveResult.success()).thenReturn(false);
            when(pgClient.approve(any())).thenReturn(approveResult);

            paymentService.confirm(1L, new ConfirmPaymentRequest("PG_KEY_123"), "idem-key", 100L);

            verify(pointService, never()).rollbackPoint(any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("PG 승인이 성공하면 PaymentConfirmEvent가 발생한다.")
        void confirmSuccessPublishesOrderCompletionEvent() {
            when(paymentTxOps.assignKeyAndCommit(eq(1L), any()))
                    .thenAnswer(inv -> { payment.markPending(); return new PaymentTxOps.ReadyPaymentContext(payment, 0L); });
            when(paymentTxOps.applyConfirmResult(eq(1L), any()))
                    .thenAnswer(inv -> { payment.confirmVerifiedSuccess(); return payment; });

            PgApproveResult approveResult = mock(PgApproveResult.class);
            when(approveResult.success()).thenReturn(true);
            when(pgClient.approve(any())).thenReturn(approveResult);

            paymentService.confirm(1L, new ConfirmPaymentRequest("PG_KEY_123"), "idem-key", 100L);

            ArgumentCaptor<PaymentConfirmEvent> captor =
                    ArgumentCaptor.forClass(PaymentConfirmEvent.class);
            verify(paymentResultEventPublisher).publishConfirmed(captor.capture());
            assertThat(captor.getValue().orderId()).isEqualTo(payment.getOrderId());
            assertThat(captor.getValue().paymentId()).isEqualTo(payment.getId());
        }
    }

    @Nested
    @DisplayName("결제 실패")
    class Fail {
        private Payment payment;

        @BeforeEach
        void setUp() {
            payment = Payment.create(1L, 100L, 10000L);
            payment.assignPaymentKey("PG_KEY_123");
        }

        @Test
        @DisplayName("READY 상태의 결제는 실패 처리되고 PG 취소 요청이 나간다.")
        void failSuccess() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            FailPaymentResponse response = paymentService.fail(1L, new FailPaymentRequest("고객 요청"), 100L);

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(response).isNotNull();
            verify(pgClient).cancel(any());
        }

        @Test
        @DisplayName("결제를 찾을 수 없을면 예외를 발생한다.")
        void failPaymentNotFound() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.fail(1L, new FailPaymentRequest("고객 요청"), 100L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);

            verifyNoInteractions(pgClient);
        }

        @Test
        @DisplayName("PAID 상태의 결제는 실패 처리할 수 없고 PG 요청도 나가지 않는다.")
        void failInvalidStatus() {
            payment.approve();

            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.fail(1L, new FailPaymentRequest("고객 요청"), 100L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS);

            verifyNoInteractions(pgClient);
        }

        @Test
        @DisplayName("이미 FAILED된 결제에 다시 호출하면 중복 무시되고 PG 요청은 나가지 않는다.")
        void failAlreadyFailed() {
            payment.fail();

            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            FailPaymentResponse response = paymentService.fail(1L, new FailPaymentRequest("고객 요청"), 100L);

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(response).isNotNull();

            verifyNoInteractions(pgClient);

        }

        @Test
        @DisplayName("PG 취소 요청이 실패하면 예외가 발생한다.")
        void failPgRequestFailed() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            doThrow(new BusinessException(PaymentErrorCode.PG_REQUEST_FAILED)).when(pgClient).cancel(any());

            assertThatThrownBy(() -> paymentService.fail(1L, new FailPaymentRequest("PG사 취소 요청"), 100L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PG_REQUEST_FAILED);

            verifyNoInteractions(pointService);
        }

        @Test
        @DisplayName("포인트를 사용한 결제가 실패 처리되면 PG 취소와 포인트 롤백이 모두 일어난다.")
        void failWithPointAndPaymentKey() {
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(pointService.findUsedAmount("ORDER:1:POINT_USE")).thenReturn(3000L);

            paymentService.fail(1L, new FailPaymentRequest("고객 요청"), 100L);

            verify(pgClient).cancel(any());
            verify(pointService).rollbackPoint("ORDER:1:POINT_USE", 3000L, "ORDER:1:POINT_ROLLBACK_FAIL", true);
        }

        @Test
        @DisplayName("paymentKey가 없어도 사용했던 포인트는 롤백돼야 한다.")
        void failWithPointButNoPaymentKey() {
            Payment noKeyPayment = Payment.create(1L, 100L, 10000L);
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(noKeyPayment));
            when(pointService.findUsedAmount("ORDER:1:POINT_USE")).thenReturn(3000L);

            paymentService.fail(1L, new FailPaymentRequest("고객 요청"), 100L);

            assertThat(noKeyPayment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            verifyNoInteractions(pgClient);
            verify(pointService).rollbackPoint("ORDER:1:POINT_USE", 3000L, "ORDER:1:POINT_ROLLBACK_FAIL", true);
        }

        @Test
        @DisplayName("이미 FAILED된 결제는 포인트 롤백도 다시 일어나지 않는다.")
        void failAlreadyFailedDoesNotRollbackPointAgain() {
            payment.fail(); // 이미 FAILED 상태로 세팅

            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            paymentService.fail(1L, new FailPaymentRequest("고객 요청"), 100L);

            verifyNoInteractions(pgClient, pointService);
        }
    }

    @Nested
    @DisplayName("결제 내역 조회")
    class GetPaymentHistory {

        @Test
        @DisplayName("사용자의 결제 내역을 조회한다.")
        void getPaymentHistorySuccess() {
            Payment payment1 = Payment.create(1L, 100L, 10000L);
            Payment payment2 = Payment.create(2L, 100L, 20000L);
            Pageable pageable = PageRequest.of(0, 10);
            Page<Payment> paymentPage = new PageImpl<>(List.of(payment1, payment2), pageable, 2);

            when(paymentRepository.findByUserId(100L, pageable)).thenReturn(paymentPage);

            Page<GetPaymentHistoryResponse> result = paymentService.getPaymentHistory(100L, pageable);

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).hasSize(2);
        }
    }

    @Test
    @DisplayName("결제 내역이 없으면 빈 페이지를 반환한다.")
    void getPaymentHistoryEmpty() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Payment> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(paymentRepository.findByUserId(100L, pageable)).thenReturn(emptyPage);

        Page<GetPaymentHistoryResponse> result = paymentService.getPaymentHistory(100L, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Nested
    @DisplayName("전액 환불")
    class FullRefund {
        @Test
        @DisplayName("전액 취소 중 동시성 충돌이 발생하면 예외가 발생하고 PG 요청은 나가지 않는다.")
        void refundConcurrentModification() {
            Payment payment = Payment.create(1L, 100L, 10000L);
            payment.assignPaymentKey("PG_KEY_123");
            payment.approve();

            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));
            when(orderClient.getTicketId(1L)).thenReturn(10L);
            when(performanceClient.getPerformanceDate(10L)).thenReturn(LocalDate.now().plusDays(4));
            when(paymentRepository.saveAndFlush(payment))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Payment.class, 1L));

            assertThatThrownBy(() -> paymentService.refund(1L, new RefundPaymentRequest("고객 요청")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_CONCURRENT_MODIFICATION);

            verifyNoInteractions(pgClient);
        }

        @Test
        @DisplayName("환불 가능 기간이 지나면 접수 자체가 거부되고 결제 상태는 그대로 유지된다.")
        void refundRejectedWhenPeriodExpired() {
            Payment payment = Payment.create(1L, 100L, 10000L);
            payment.assignPaymentKey("PG_KEY_123");
            payment.approve();

            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));
            when(orderClient.getTicketId(1L)).thenReturn(10L);
            when(performanceClient.getPerformanceDate(10L)).thenReturn(LocalDate.now()); // 공연 당일 -> rate 0.0

            assertThatThrownBy(() -> paymentService.refund(1L, new RefundPaymentRequest("고객 요청")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PaymentErrorCode.REFUND_PERIOD_EXPIRED);

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            verifyNoInteractions(pgClient, paymentRefundRepository);
            verify(paymentRepository, never()).saveAndFlush(any());
            verify(refundEventPublisher, never()).publish(any());
        }
    }

    @Nested
    @DisplayName("환불 처리")
    class onRefund {

        @Test
        @DisplayName("환불률이 0보다 크고 PG 취소가 성공하면 환불이 완료 처리된다.")
        void refundSuccess() {
            //given
            Payment payment = Payment.create(1L, 100L, 10000L);
            ReflectionTestUtils.setField(payment, "id", 1L);
            payment.assignPaymentKey("PG_KEY_123");
            payment.approve();
            payment.requestRefund();

            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(orderClient.getTicketId(1L)).thenReturn(10L);
            when(performanceClient.getPerformanceDate(10L)).thenReturn(LocalDate.now().plusDays(4)); // refundRate 0.4
            when(pgClient.cancel(any(PgCancelCommand.class)))
                    .thenReturn(new PgCancelResult(true, LocalDateTime.now()));

            //when
            paymentService.onRefundRequested(new RefundRequestEvent(1L, "고객 요청", LocalDateTime.now()));

            //then
            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(payment.getRefundedAmount()).isEqualTo(4000L); // 10000 * 0.4

            ArgumentCaptor<PgCancelCommand> commandCaptor = ArgumentCaptor.forClass(PgCancelCommand.class);
            verify(pgClient).cancel(commandCaptor.capture());
            assertThat(commandCaptor.getValue().cancelAmount()).isEqualTo(4000L);

            ArgumentCaptor<PaymentRefund> refundCaptor = ArgumentCaptor.forClass(PaymentRefund.class);
            verify(paymentRefundRepository).save(refundCaptor.capture());
            assertThat(refundCaptor.getValue().getRefundAmount()).isEqualTo(4000L);

            verify(refundEventPublisher).publishCompleted(any());

        }
    }

    @Test
    @DisplayName("환불율이 0이면(공연 당일 이후) PG 호출 없이 결제가 PAID로 롤백된다.")
    void refundRateZero() {
        //given
        Payment payment = Payment.create(1L, 100L, 10000L);
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.assignPaymentKey("PG_KEY_123");
        payment.approve();
        payment.requestRefund();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getTicketId(1L)).thenReturn(10L);
        when(performanceClient.getPerformanceDate(10L)).thenReturn(LocalDate.now()); // 공연 당일 -> rate 0.0

        //when
        paymentService.onRefundRequested(new RefundRequestEvent(1L, "고객 요청", LocalDateTime.now()));

        //then
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(pgClient);
        verifyNoInteractions(paymentRefundRepository);

        ArgumentCaptor<RefundFailedEvent> eventCaptor = ArgumentCaptor.forClass(RefundFailedEvent.class);
        verify(refundEventPublisher).publishFailed(eventCaptor.capture());
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(payment.getOrderId());
        verify(refundEventPublisher, never()).publishCompleted(any());

    }

    @Test
    @DisplayName("PG 취소가 실패하면 결제가 전부 PAID로 롤백되고 환불 이력이 저장되지 않는다.")
    void pgCancelFails() {
        //given
        Payment payment = Payment.create(1L, 100L, 10000L);
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.assignPaymentKey("PG_KEY_123");
        payment.approve();
        payment.requestRefund();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getTicketId(1L)).thenReturn(10L);
        when(performanceClient.getPerformanceDate(10L)).thenReturn(LocalDate.now().plusDays(6)); // rate 1.0
        when(pgClient.cancel(any(PgCancelCommand.class)))
                .thenReturn(new PgCancelResult(false, null));

        //when
        paymentService.onRefundRequested(new RefundRequestEvent(1L, "고객 요청", LocalDateTime.now()));

        //then
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(paymentRefundRepository); // return 누락 상태면 이 테스트가 실패함
        verify(refundEventPublisher).publishFailed(any());
        verify(refundEventPublisher, never()).publishCompleted(any());
    }

    @Test
    @DisplayName("외부 조회 중 예외가 발생하면 결제가 PAID로 롤백된다.")
    void externalCallThrows() {
        //given
        Payment payment = Payment.create(1L, 100L, 10000L);
        payment.assignPaymentKey("PG_KEY_123");
        payment.approve();
        payment.requestRefund();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getTicketId(1L)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        //when
        paymentService.onRefundRequested(new RefundRequestEvent(1L, "고객 요청", LocalDateTime.now()));

        //then
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(pgClient);
        verifyNoInteractions(paymentRefundRepository);

        verify(refundEventPublisher).publishFailed(any());
        verify(refundEventPublisher, never()).publishCompleted(any());
    }

    @Test
    @DisplayName("포인트를 사용한 결제가 환불되면 환불율만큼 포인트도 환급된다.")
    void refundSuccessWithPoint() {
        //given
        Payment payment = Payment.create(1L, 100L, 10000L);
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.assignPaymentKey("PG_KEY_123");
        payment.approve();
        payment.requestRefund();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getTicketId(1L)).thenReturn(10L);
        when(performanceClient.getPerformanceDate(10L)).thenReturn(LocalDate.now().plusDays(4));
        when(pgClient.cancel(any(PgCancelCommand.class)))
                .thenReturn(new PgCancelResult(true, LocalDateTime.now()));
        when(pointService.findUsedAmount("ORDER:1:POINT_USE")).thenReturn(3000L);

        // when
        paymentService.onRefundRequested(new RefundRequestEvent(1L, "고객 요청", LocalDateTime.now()));

        // then
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(pointService).rollbackPoint("ORDER:1:POINT_USE", 1200L, "ORDER:1:POINT_ROLLBACK_REFUND", false);
    }

    @Test
    @DisplayName("포인트를 사용하지 않은 결제가 환불되면 포인트 롤백은 시도되지 않는다.")
    void refundSuccessWithoutPoint() {
        //given
        Payment payment = Payment.create(1L, 100L, 10000L);
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.assignPaymentKey("PG_KEY_123");
        payment.approve();
        payment.requestRefund();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getTicketId(1L)).thenReturn(10L);
        when(performanceClient.getPerformanceDate(10L)).thenReturn(LocalDate.now().plusDays(4));
        when(pgClient.cancel(any(PgCancelCommand.class)))
                .thenReturn(new PgCancelResult(true, LocalDateTime.now()));

        // when
        paymentService.onRefundRequested(new RefundRequestEvent(1L, "고객 요청", LocalDateTime.now()));

        // then
        verify(pointService, never()).rollbackPoint(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("포인트 환급이 동시성 충돌로 계속 실패하면 3번 재시도 후 포기하고, 환불 자체는 완료 상태로 유지한다.")
    void refundPointRollbackExhaustsRetries() {
        //given
        Payment payment = Payment.create(1L, 100L, 10000L);
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.assignPaymentKey("PG_KEY_123");
        payment.approve();
        payment.requestRefund();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getTicketId(1L)).thenReturn(10L);
        when(performanceClient.getPerformanceDate(10L)).thenReturn(LocalDate.now().plusDays(4));
        when(pgClient.cancel(any(PgCancelCommand.class)))
                .thenReturn(new PgCancelResult(true, LocalDateTime.now()));
        when(pointService.findUsedAmount("ORDER:1:POINT_USE")).thenReturn(3000L);
        doThrow(new BusinessException(PointErrorCode.POINT_CONCURRENT_MODIFICATION))
                .when(pointService).rollbackPoint(any(), any(), any(), anyBoolean());

        // when
        paymentService.onRefundRequested(new RefundRequestEvent(1L, "고객 요청", LocalDateTime.now()));

        //then
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(pointService, times(3)).rollbackPoint(any(), any(), any(), anyBoolean());
        verify(refundEventPublisher).publishCompleted(any());
        verify(refundEventPublisher, never()).publishFailed(any());
    }

    @Test
    @DisplayName("포인트 환급이 첫 시도에서만 동시성 충돌이면 재시도 후 성공하고, 더 이상 재시도하지 않는다.")
    void refundPointRollbackSucceedsOnRetry() {
        //given
        Payment payment = Payment.create(1L, 100L, 10000L);
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.assignPaymentKey("PG_KEY_123");
        payment.approve();
        payment.requestRefund();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getTicketId(1L)).thenReturn(10L);
        when(performanceClient.getPerformanceDate(10L)).thenReturn(LocalDate.now().plusDays(4));
        when(pgClient.cancel(any(PgCancelCommand.class)))
                .thenReturn(new PgCancelResult(true, LocalDateTime.now()));
        when(pointService.findUsedAmount("ORDER:1:POINT_USE")).thenReturn(3000L);
        doThrow(new BusinessException(PointErrorCode.POINT_CONCURRENT_MODIFICATION))
                .doNothing()
                .when(pointService).rollbackPoint(any(), any(), any(), anyBoolean());

        //when
        paymentService.onRefundRequested(new RefundRequestEvent(1L, "고객 요청", LocalDateTime.now()));

        //then
        verify(pointService, times(2)).rollbackPoint(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("포인트 환급이 재시도 불가능한 예외로 실패하면 재시도 없이 즉시 포기한다.")
    void refundPointRollbackNonRetryableFailsImmediately() {
        //given
        Payment payment = Payment.create(1L, 100L, 10000L);
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.assignPaymentKey("PG_KEY_123");
        payment.approve();
        payment.requestRefund();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getTicketId(1L)).thenReturn(10L);
        when(performanceClient.getPerformanceDate(10L)).thenReturn(LocalDate.now().plusDays(4));
        when(pgClient.cancel(any(PgCancelCommand.class)))
                .thenReturn(new PgCancelResult(true, LocalDateTime.now()));
        when(pointService.findUsedAmount("ORDER:1:POINT_USE")).thenReturn(3000L);
        doThrow(new BusinessException(PointErrorCode.ORIGIN_POINT_LOG_NOT_FOUND, "ORDER:1:POINT_USE"))
                .when(pointService).rollbackPoint(any(), any(), any(), anyBoolean());

        //when
        paymentService.onRefundRequested(new RefundRequestEvent(1L, "고객 요청", LocalDateTime.now()));

        //then
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(pointService, times(1)).rollbackPoint(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("공연일 조회에서 PERFORMANCE_DATE_NOT_FOUND가 발생하면 결제가 PAID로 롤백된다.")
    void performanceDateNotFound() {
        //given
        Payment payment = Payment.create(1L, 100L, 10000L);
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.assignPaymentKey("PG_KEY_123");
        payment.approve();
        payment.requestRefund();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getTicketId(1L)).thenReturn(10L);
        when(performanceClient.getPerformanceDate(10L))
                .thenThrow(new BusinessException(PaymentErrorCode.PERFORMANCE_DATE_NOT_FOUND, 10L));

        //when
        paymentService.onRefundRequested(new RefundRequestEvent(1L, "고객 요청", LocalDateTime.now()));

        //then
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(pgClient);
        verifyNoInteractions(paymentRefundRepository);
        verify(refundEventPublisher).publishFailed(any());
        verify(refundEventPublisher, never()).publishCompleted(any());
    }

    @Test
    @DisplayName("performance-service 요청이 실패(5xx 등)해도 결제가 PAID로 롤백된다.")
    void performanceServiceRequestFailed() {
        //given
        Payment payment = Payment.create(1L, 100L, 10000L);
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.assignPaymentKey("PG_KEY_123");
        payment.approve();
        payment.requestRefund();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getTicketId(1L)).thenReturn(10L);
        when(performanceClient.getPerformanceDate(10L))
                .thenThrow(new BusinessException(PaymentErrorCode.PERFORMANCE_SERVICE_REQUEST_FAILED));

        //when
        paymentService.onRefundRequested(new RefundRequestEvent(1L, "고객 요청", LocalDateTime.now()));

        //then
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(pgClient);
        verifyNoInteractions(paymentRefundRepository);
        verify(refundEventPublisher).publishFailed(any());
        verify(refundEventPublisher, never()).publishCompleted(any());
    }

    @Test
    @DisplayName("포인트 환급 중 예상치 못한 RuntimeException이 발생해도 환불 자체는 완료 상태로 유지된다.")
    void refundSuccessButPointRollbackThrowsUnexceptedException() {
        Payment payment = Payment.create(1L, 100L, 10000L);
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.assignPaymentKey("PG_KEY_123");
        payment.approve();
        payment.requestRefund();

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getTicketId(1L)).thenReturn(10L);

        when(performanceClient.getPerformanceDate(10L)).thenReturn(LocalDate.now().plusDays(4));
        when(pgClient.cancel(any(PgCancelCommand.class)))
                .thenReturn(new PgCancelResult(true, LocalDateTime.now()));
        when(pointService.findUsedAmount("ORDER:1:POINT_USE")).thenReturn(3000L);
        doThrow(new RuntimeException("unexpected"))
                .when(pointService).rollbackPoint(any(), any(), any(), anyBoolean());

        paymentService.onRefundRequested(new RefundRequestEvent(1L, "고객 요청", LocalDateTime.now()));

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(refundEventPublisher).publishCompleted(any());
        verify(refundEventPublisher, never()).publishFailed(any());
    }
}
