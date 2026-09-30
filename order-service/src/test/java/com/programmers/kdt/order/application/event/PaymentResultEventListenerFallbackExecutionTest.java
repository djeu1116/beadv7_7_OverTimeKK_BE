package com.programmers.kdt.order.application.event;

import com.programmers.kdt.common.contract.CompensationCompletedEvent;
import com.programmers.kdt.common.contract.PaymentConfirmEvent;
import com.programmers.kdt.common.contract.PaymentFailEvent;
import com.programmers.kdt.common.contract.RefundCompletedEvent;
import com.programmers.kdt.common.contract.RefundFailedEvent;
import com.programmers.kdt.order.application.service.OrderService;
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

// 주문 내부 컨트롤러(OrderEventInternalController)는 결제가 보낸 이벤트를 트랜잭션 없이
// publishEvent()만으로 발행한다(HTTP 요청 처리 중이라 애초에 활성 트랜잭션이 없음). 이 상황에서도
// 주문 쪽 리스너들이 fallbackExecution=true 덕분에 실행되는지 확인한다. 물리 분리 전에는 이 시나리오를
// 결제 쪽 publisher까지 같이 넣어 재현했지만(SpringPaymentResultEventPublisher 등), 분리 후에는
// order-service가 결제 코드를 참조할 수 없으므로 order-service 자신의 ApplicationEventPublisher로
// 직접 발행해 같은 시나리오를 재현한다 - 실제 내부 컨트롤러가 하는 일과 동일하다.
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
    }

    private AnnotationConfigApplicationContext context;
    private ApplicationEventPublisher eventPublisher;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        eventPublisher = context;
        orderService = context.getBean(OrderService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("재조회 스케줄러처럼 활성 트랜잭션 없이 이벤트를 발행해도 fallbackExecution 덕분에 리스너가 실행된다.")
    void publishConfirmedWithoutActiveTransaction_stillInvokesListener() {
        eventPublisher.publishEvent(new PaymentConfirmEvent(1L, 1L));

        verify(orderService).completeOrder(1L);
    }

    @Test
    @DisplayName("트랜잭션 안에서 발행하면 커밋 전에는 리스너가 실행되지 않고, 커밋 후에만 실행된다.")
    void publishConfirmedInsideTransaction_invokesListenerOnlyAfterCommit() {
        TransactionTemplate txTemplate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

        txTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new PaymentConfirmEvent(2L, 2L));
            verifyNoInteractions(orderService);
        });

        verify(orderService).completeOrder(2L);
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 AFTER_COMMIT 리스너는 끝까지 실행되지 않는다.")
    void publishConfirmedThenRollback_neverInvokesListener() {
        TransactionTemplate txTemplate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

        txTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new PaymentConfirmEvent(3L, 3L));
            status.setRollbackOnly();
        });

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("PaymentFailEvent도 활성 트랜잭션 없이 발행되면 fallbackExecution으로 실행된다.")
    void publishFailedWithoutActiveTransaction_stillInvokesListener() {
        eventPublisher.publishEvent(new PaymentFailEvent(4L, 4L, "타임아웃"));

        verify(orderService).handlePaymentFailed(4L);
    }

    @Test
    @DisplayName("RefundCompletedEvent도 활성 트랜잭션 없이 발행되면(outbox relay) 주문 취소 확정 리스너가 실행된다.")
    void publishRefundCompletedWithoutActiveTransaction_stillInvokesListener() {
        eventPublisher.publishEvent(new RefundCompletedEvent(5L, 50L));

        verify(orderService).confirmCancellation(5L);
    }

    @Test
    @DisplayName("RefundFailedEvent도 활성 트랜잭션 없이 발행되면(outbox relay) 주문 취소 접수 복구 리스너가 실행된다.")
    void publishRefundFailedWithoutActiveTransaction_stillInvokesListener() {
        eventPublisher.publishEvent(new RefundFailedEvent(6L, 60L, "PG_REQUEST_FAILED"));

        verify(orderService).revertCancellation(6L);
    }

    @Test
    @DisplayName("CompensationCompletedEvent도 활성 트랜잭션 없이 발행되면(outbox relay) 보상 완료 리스너가 실행된다.")
    void publishCompensationCompletedWithoutActiveTransaction_stillInvokesListener() {
        eventPublisher.publishEvent(new CompensationCompletedEvent(7L, 70L));

        verify(orderService).failOrderAfterCompensation(7L);
    }
}
