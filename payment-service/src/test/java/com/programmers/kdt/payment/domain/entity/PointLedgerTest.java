package com.programmers.kdt.payment.domain.entity;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.domain.exception.PointErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointLedgerTest {

    @Test
    @DisplayName("생성하면 잔여 환급 한도가 사용액과 같다.")
    void create() {
        PointLedger ledger = PointLedger.create(1L, 10L, 3000L);

        assertThat(ledger.getUserId()).isEqualTo(1L);
        assertThat(ledger.getUseLogId()).isEqualTo(10L);
        assertThat(ledger.getUsedAmount()).isEqualTo(3000L);
        assertThat(ledger.getRemainingRefundable()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("consume()하면 그만큼 잔여 한도에서 차감된다.")
    void consume() {
        PointLedger ledger = PointLedger.create(1L, 10L, 3000L);

        ledger.consume(1000L);

        assertThat(ledger.getRemainingRefundable()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("여러 번 나눠서 consume해도 누적으로 차감된다 - 이게 곧 누적 환급액 검증이다.")
    void consumeAccumulates() {
        PointLedger ledger = PointLedger.create(1L, 10L, 3000L);

        ledger.consume(1000L);
        ledger.consume(1000L);

        assertThat(ledger.getRemainingRefundable()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("잔여 한도를 넘는 금액을 consume하려 하면 LEDGER_REMAINING_EXCEEDED 예외가 발생하고 잔여 한도는 그대로다.")
    void consumeExceedsRemaining() {
        PointLedger ledger = PointLedger.create(1L, 10L, 3000L);
        ledger.consume(2000L); // remaining = 1000

        assertThatThrownBy(() -> ledger.consume(1500L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PointErrorCode.LEDGER_REMAINING_EXCEEDED);

        assertThat(ledger.getRemainingRefundable()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("정확히 잔여 한도만큼 consume하면 0이 되고 성공한다.")
    void consumeExactRemaining() {
        PointLedger ledger = PointLedger.create(1L, 10L, 3000L);

        ledger.consume(3000L);

        assertThat(ledger.getRemainingRefundable()).isEqualTo(0L);
    }
}
