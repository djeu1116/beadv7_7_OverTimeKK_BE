package com.programmers.kdt.payment.entity;

import com.programmers.kdt.common.entity.BaseTimeEntity;
import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.exception.PointErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 포인트 사용 건 하나(PointLog USE 1건)마다 "아직 환급 가능한 금액이 얼마 남았는지"를 관리한다.
// PointLog.rollback()의 개별 검증(취소 금액이 원본을 넘는지)만으로는 같은 사용 건에 대해
// 서로 다른 rollbackEventId로 두 번 환급이 걸리는 걸 못 막는다(각자 원본 대비로는 유효해 보이므로) -
// 이 원장이 누적 환급액을 실제로 관리해서 그 이중 환급을 막는다.
@Entity
@Table(name = "point_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointLedger extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "use_log_id", nullable = false, unique = true)
    private Long useLogId;

    @Column(name = "used_amount", nullable = false)
    private Long usedAmount;

    @Column(name = "remaining_refundable", nullable = false)
    private Long remainingRefundable;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static PointLedger create(Long userId, Long useLogId, Long usedAmount) {
        PointLedger ledger = new PointLedger();
        ledger.userId = userId;
        ledger.useLogId = useLogId;
        ledger.usedAmount = usedAmount;
        ledger.remainingRefundable = usedAmount;
        return ledger;
    }

    // 환급 요청 금액만큼 잔여 한도에서 차감한다. 이미 다른 경로로 환급된 만큼은 잔여 한도에서
    // 빠져 있으므로, 여기서 막히면 그게 곧 "누적 환급액 초과"다.
    public void consume(Long amount) {
        if (amount > remainingRefundable) {
            throw new BusinessException(PointErrorCode.LEDGER_REMAINING_EXCEEDED, remainingRefundable, amount);
        }
        this.remainingRefundable -= amount;
    }
}
