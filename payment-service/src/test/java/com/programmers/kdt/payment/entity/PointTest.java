package com.programmers.kdt.payment.entity;

import com.programmers.kdt.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PointTest {

    @Nested
    @DisplayName("포인트 생성")
    class CreatePoint {

        @Test
        @DisplayName("정상적인 userId로 생성하면 totalPoint가 0으로 초기화된다.")
        void createPoint() {
            //given
            Long userId = 1L;

            //when
            Point point = Point.create(userId);

            //then
            assertThat(point.getUserId()).isEqualTo(userId);
            assertThat(point.getTotalPoint()).isEqualTo(0L);
        }

        @Test
        @DisplayName("userId가 null이면 예외가 발생한다.")
        void createPointNullUserId() {
            assertThatThrownBy(() -> Point.create(null))
                    .isInstanceOf(BusinessException.class);
        }
    }


    @Nested
    @DisplayName("포인트 적립")
    class EarnPoint {

        @Test
        @DisplayName("정상적으로 적립하면 totalPoint가 증가한다.")
        void earnPoint() {
            //given
            Point point = Point.create(1L);

            //when
            point.earn(500L);

            //then
            assertThat(point.getTotalPoint()).isEqualTo(500L);
        }

        @Test
        @DisplayName("여러 번 적립하면 누적된다.")
        void earnPointAccumulates() {
            //given
            Point point = Point.create(1L);

            //when
            point.earn(500L);
            point.earn(400L);

            //then
            assertThat(point.getTotalPoint()).isEqualTo(900L);
        }

        @Test
        @DisplayName("적립 금액이 null이거나 0 이하이면 예외가 발생한다.")
        void earnPointInvalidAmount() {
            Point point = Point.create(1L);

            assertThatThrownBy(() -> point.earn(null))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> point.earn(0L))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> point.earn(-1000L))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("포인트 사용")
    class UsePoint {

        @Test
        @DisplayName("정상적으로 사용하면 totalPoint가 차감된다.")
        void usePoint() {
            //given
            Point point = Point.create(1L);
            point.earn(1000L);

            //when
            point.use(500L);

            //then
            assertThat(point.getTotalPoint()).isEqualTo(500L);
        }

        @Test
        @DisplayName("보유 포인트를 정확히 전부 사용하면 0원이 된다.")
        void usePointExactBalance() {
            //given
            Point point = Point.create(1L);
            point.earn(500L);

            //when
            point.use(500L);

            //then
            assertThat(point.getTotalPoint()).isEqualTo(0L);
        }

        @Test
        @DisplayName("보유 포인트보다 많이 사용하면 예외가 발생한다.")
        void usePointFailWhenInsufficient() {
            //given
            Point point = Point.create(1L);
            point.earn(500L);

            //when & then
            assertThatThrownBy(() -> point.use(600L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("사용 금액이 null이거나 0 이하이면 예외가 발생한다.")
        void usePointInvalidAmount() {
            Point point = Point.create(1L);
            point.earn(500L);

            assertThatThrownBy(() -> point.use(null))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> point.use(0L))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> point.use(-1000L))
                    .isInstanceOf(RuntimeException.class);

        }
    }

    @Nested
    @DisplayName("포인트 환급")
    class RollbackPoint {

        @Test
        @DisplayName("정상적으로 롤백하면 totalPoint가 증가한다.")
        void rollbackPoint() {
            //given
            Point point = Point.create(1L);
            point.earn(500L);
            point.use(300L);

            //when
            point.rollbackUse(300L);

            //then
            assertThat(point.getTotalPoint()).isEqualTo(500L);
        }

        @Test
        @DisplayName("롤백 금액이 null이거나 0 이하이면 예외가 발생한다.")
        void rollbackUseFailInvalidAmount() {
            Point point = Point.create(1L);

            assertThatThrownBy(() -> point.rollbackUse(null))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> point.rollbackUse(0L))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> point.rollbackUse(-1000L))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}

