package com.programmers.kdt.payment.domain.entity;

import com.programmers.kdt.common.entity.BaseTimeEntity;
import com.programmers.kdt.payment.infrastructure.converter.PaymentKeyConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Payment는 주문당 하나(최종 결과)를 유지하고, 재시도마다 paymentKey/pgOrderId를 덮어쓴다(retryReady()).
// 그러다 보니 이전 시도의 PG 키나 그 시도에서 실제로 쓴 포인트 같은 이력이 사라져서 감사 추적이 안 됐다 -
// PaymentAttempt는 그 이력을 시도(attemptSeq)마다 별도 행으로 남기는 용도다. Payment의 상태 전이
// 로직은 그대로 두고(설계 결정: Payment가 상태를 계속 소유), 이 엔티티는 덧붙는 기록일 뿐이다.
@Entity
@Table(name = "payment_attempt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "attempt_seq", nullable = false)
    private Integer attemptSeq;

    @Convert(converter = PaymentKeyConverter.class)
    @Column(name = "payment_key")
    private String paymentKey;

    @Column(name = "pg_order_id")
    private String pgOrderId;

    @Column(name = "used_point", nullable = false)
    private Long usedPoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentAttemptStatus status;

    public static PaymentAttempt create(Long paymentId, int attemptSeq, String pgOrderId, Long usedPoint) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.paymentId = paymentId;
        attempt.attemptSeq = attemptSeq;
        attempt.pgOrderId = pgOrderId;
        attempt.usedPoint = usedPoint;
        attempt.status = PaymentAttemptStatus.READY;
        return attempt;
    }

    public void assignPaymentKey(String paymentKey) {
        this.paymentKey = paymentKey;
    }

    public void markPending() {
        this.status = PaymentAttemptStatus.PENDING_VERIFICATION;
    }

    public void markPaid() {
        this.status = PaymentAttemptStatus.PAID;
    }

    public void markFailed() {
        this.status = PaymentAttemptStatus.FAILED;
    }
}
