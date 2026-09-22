package com.programmers.kdt.payment.service;

import com.programmers.kdt.payment.entity.Payment;
import com.programmers.kdt.payment.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = "logging.level.org.hibernate.orm.jdbc.bind=trace")
public class PaymentOptimisticLockTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("같은 결제를 동시에 수정하면 낙관적 락 예외가 발생한다.")
    void concurrentModificationThrowsException() {
        Payment payment = Payment.create(1L, 100L, 10000L);
        payment.assignPaymentKey("PG_KEY_123");
        payment.approve();
        payment.requestRefund();
        Long id = paymentRepository.saveAndFlush(payment).getId();

        entityManager.clear();
        Payment payment1 = paymentRepository.findById(id).orElseThrow();

        entityManager.clear();
        Payment payment2 = paymentRepository.findById(id).orElseThrow();

        entityManager.clear();

        payment1.completeRefund(3000L);
        paymentRepository.saveAndFlush(payment1); // 먼저 저장 → 버전 증가, 성공

        payment2.completeRefund(2000L);
        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment2))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class); // 버전이 밀려서 실패
    }
}
