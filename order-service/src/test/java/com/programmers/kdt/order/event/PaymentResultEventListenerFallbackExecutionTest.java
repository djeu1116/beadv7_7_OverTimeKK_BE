package com.programmers.kdt.order.event;

import com.programmers.kdt.order.service.OrderService;
import com.programmers.kdt.common.contract.PaymentConfirmEvent;
import com.programmers.kdt.common.contract.PaymentFailEvent;
import com.programmers.kdt.payment.client.pay.SpringPaymentResultEventPublisher;
import com.programmers.kdt.common.contract.CompensationCompletedEvent;
import com.programmers.kdt.common.contract.RefundCompletedEvent;
import com.programmers.kdt.common.contract.RefundFailedEvent;
import com.programmers.kdt.payment.client.refund.SpringRefundEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.mockito.Mockito.*;

class PaymentResultEventListenerFallbackExecutionTest {

    // 재조회 스케줄러처럼 "활성 트랜잭션이 없는" 상황을 재현하기 위한 최소 스프링 컨텍스트.
    // Boot 없이 수동 구성이라 TransactionalEventListenerFactory 빈을 직접 등록해야
    // @TransactionalEventListener가 동작함(Boot 환경에서는 자동 등록됨).
    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        static TransactionalEventListenerFactory transactionalEventListenerFactory() {
            return new TransactionalEventListenerFactory();
        }

        @Bean
        OrderService orderService() {
            return mock(OrderService.class);
        }

        @Bean
        PaymentConfirmEventListener paymentConfirmEventListener(OrderService orderService) {
            return new PaymentConfirmEventListener(orderService);
        }

        @Bean
        PaymentFailEventListener paymentFailEventListener(OrderService orderService) {
            return new PaymentFailEventListener(orderService);
        }

        @Bean
        RefundResultOrderListener refundResultOrderListener(OrderService orderService) {
            return new RefundResultOrderListener(orderService);
        }

        @Bean
        SpringRefundEventPublisher refundEventPublisher(ApplicationEventPublisher publisher) {
            return new SpringRefundEventPublisher(publisher);
        }

        @Bean
        SpringPaymentResultEventPublisher paymentResultEventPublisher(ApplicationEventPublisher publisher) {
            return new SpringPaymentResultEventPublisher(publisher);
        }
    }

    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("재조회 스케줄러처럼 활성 트랜잭션 없이 이벤트를 발행해도 fallbackExecution 덕분에 리스너가 실행된다.")
    void publishConfirmedWithoutActiveTransaction_stillInvokesListener() {
        SpringPaymentResultEventPublisher publisher = context.getBean(SpringPaymentResultEventPublisher.class);
        OrderService orderService = context.getBean(OrderService.class);

        publisher.publishConfirmed(new PaymentConfirmEvent(1L, 1L));

        verify(orderService).completeOrder(1L);
    }

    @Test
    @DisplayName("트랜잭션 안에서 발행하면 커밋 전에는 리스너가 실행되지 않고, 커밋 후에만 실행된다.")
    void publishConfirmedInsideTransaction_invokesListenerOnlyAfterCommit() {
        SpringPaymentResultEventPublisher publisher = context.getBean(SpringPaymentResultEventPublisher.class);
        OrderService orderService = context.getBean(OrderService.class);
        TransactionTemplate txTemplate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

        txTemplate.executeWithoutResult(status -> {
            publisher.publishConfirmed(new PaymentConfirmEvent(2L, 2L));
            verifyNoInteractions(orderService);
        });

        verify(orderService).completeOrder(2L);
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 AFTER_COMMIT 리스너는 끝까지 실행되지 않는다.")
    void publishConfirmedThenRollback_neverInvokesListener() {
        SpringPaymentResultEventPublisher publisher = context.getBean(SpringPaymentResultEventPublisher.class);
        OrderService orderService = context.getBean(OrderService.class);
        TransactionTemplate txTemplate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

        txTemplate.executeWithoutResult(status -> {
            publisher.publishConfirmed(new PaymentConfirmEvent(3L, 3L));
            status.setRollbackOnly();
        });

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("PaymentFailEvent도 활성 트랜잭션 없이 발행되면 fallbackExecution으로 실행된다.")
    void publishFailedWithoutActiveTransaction_stillInvokesListener() {
        SpringPaymentResultEventPublisher publisher = context.getBean(SpringPaymentResultEventPublisher.class);
        OrderService orderService = context.getBean(OrderService.class);

        publisher.publishFailed(new PaymentFailEvent(4L, 4L, "타임아웃"));

        verify(orderService).handlePaymentFailed(4L);
    }

    @Test
    @DisplayName("RefundCompletedEvent도 활성 트랜잭션 없이 발행되면(outbox relay) 주문 취소 확정 리스너가 실행된다.")
    void publishRefundCompletedWithoutActiveTransaction_stillInvokesListener() {
        SpringRefundEventPublisher publisher = context.getBean(SpringRefundEventPublisher.class);
        OrderService orderService = context.getBean(OrderService.class);

        publisher.publishCompleted(new RefundCompletedEvent(5L, 50L));

        verify(orderService).confirmCancellation(5L);
    }

    @Test
    @DisplayName("RefundFailedEvent도 활성 트랜잭션 없이 발행되면(outbox relay) 주문 취소 접수 복구 리스너가 실행된다.")
    void publishRefundFailedWithoutActiveTransaction_stillInvokesListener() {
        SpringRefundEventPublisher publisher = context.getBean(SpringRefundEventPublisher.class);
        OrderService orderService = context.getBean(OrderService.class);

        publisher.publishFailed(new RefundFailedEvent(6L, 60L, "PG_REQUEST_FAILED"));

        verify(orderService).revertCancellation(6L);
    }

    @Test
    @DisplayName("CompensationCompletedEvent도 활성 트랜잭션 없이 발행되면(outbox relay) 보상 완료 리스너가 실행된다.")
    void publishCompensationCompletedWithoutActiveTransaction_stillInvokesListener() {
        SpringRefundEventPublisher publisher = context.getBean(SpringRefundEventPublisher.class);
        OrderService orderService = context.getBean(OrderService.class);

        publisher.publishCompensationCompleted(new CompensationCompletedEvent(7L, 70L));

        verify(orderService).failOrderAfterCompensation(7L);
    }
}
