package com.programmers.kdt.payment.entity;

import com.programmers.kdt.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class PointLogTest {

    @Nested
    @DisplayName("적립 로그 생성")
    class EarnLog {

        @Test
        @DisplayName("정상적으로 생성하면 EARN 타입으로 생성된다.")
        void createEarnLog() {
            //given
            Long userId = 1L;
            Long amount = 500L;
            String eventId = "evt-1";

            //when
            PointLog pointLog = PointLog.earn(userId, amount, eventId);

            //then
            assertThat(pointLog.getUserId()).isEqualTo(userId);
            assertThat(pointLog.getAmount()).isEqualTo(amount);
            assertThat(pointLog.getPointType()).isEqualTo(PointType.EARN);
            assertThat(pointLog.getEventId()).isEqualTo(eventId);
            assertThat(pointLog.getRefLogId()).isNull();
        }

        @Test
        @DisplayName("userId, amount, eventId가 유효하지 않으면 예외가 발생한다.")
        void createInvalidEarnLog() {
            assertThatThrownBy(() -> PointLog.earn(null, 500L, "evt-1"))
                    .isInstanceOf(BusinessException.class); // userId가 null
            assertThatThrownBy(() -> PointLog.earn(1L, 0L, "evt-1"))
                    .isInstanceOf(BusinessException.class); // amount가 0 이하
            assertThatThrownBy(() -> PointLog.earn(1L, -500L, "evt-1"))
                    .isInstanceOf(BusinessException.class); // amount가 음수
            assertThatThrownBy(() -> PointLog.earn(1L, 500L, null))
                    .isInstanceOf(BusinessException.class); // eventId가 null
            assertThatThrownBy(() -> PointLog.earn(1L, 500L, " "))
                    .isInstanceOf(BusinessException.class); // eventId가 빈칸
        }
    }

    @Nested
    @DisplayName("사용 로그 생성")
    class UseLog {

        @Test
        @DisplayName("정상적으로 생성하면 USE 타입으로 생성된다.")
        void createUseLog() {
            //when
            PointLog pointLog = PointLog.use(1L, 500L, "evt-1");

            //then
            assertThat(pointLog.getPointType()).isEqualTo(PointType.USE);
            assertThat(pointLog.getAmount()).isEqualTo(500L);
        }

        @Test
        @DisplayName("userId, amount, eventId가 유효하지 않으면 예외가 발생한다.")
        void createInvalidUseLog() {
            assertThatThrownBy(() -> PointLog.use(null, 500L, "evt-1"))
                    .isInstanceOf(BusinessException.class); //userId가 null
            assertThatThrownBy(() -> PointLog.use(1L, 0L, "evt-1"))
                    .isInstanceOf(BusinessException.class); // amount가 0 이하
            assertThatThrownBy(() -> PointLog.use(1L, -500L, "evt-1"))
                    .isInstanceOf(BusinessException.class); // amount가 0 이하
            assertThatThrownBy(() -> PointLog.use(1L, 500L, null))
                    .isInstanceOf(BusinessException.class); // eventId가 null
            assertThatThrownBy(() -> PointLog.use(1L, 500L, " "))
                    .isInstanceOf(BusinessException.class); // eventId가 빈칸
        }
    }

    @Nested
    @DisplayName("환급 로그 생성")
    class RollbackLog {

        @Test
        @DisplayName("정상적으로 전액 취소하면 CANCELLED 타입으로 생성되고 refLogId가 원본 id를 가리킨다.")
        void rollbackLogFull() {
            //given
            PointLog originLog = PointLog.use(1L, 500L, "evt-1");
            ReflectionTestUtils.setField(originLog, "id", 100L); // DB 없이 id 강제 주입

            //when
            PointLog cancelLog = PointLog.rollback(originLog, 500L, "evt-2", true);

            //then
            assertThat(cancelLog.getPointType()).isEqualTo(PointType.CANCELLED);
            assertThat(cancelLog.getAmount()).isEqualTo(500L);
            assertThat(cancelLog.getRefLogId()).isEqualTo(100L);
            assertThat(cancelLog.getUserId()).isEqualTo(originLog.getUserId());
        }

        @Test
        @DisplayName("부분 금액만 취소하면 PARTIAL_CANCELLED 타입으로 생성된다.")
        void rollbackLogPartial() {
            //given
            PointLog originLog = PointLog.use(1L, 500L, "evt-1");
            ReflectionTestUtils.setField(originLog, "id", 100L);

            //when
            PointLog cancelLog = PointLog.rollback(originLog, 200L, "evt-2", false);

            //then
            assertThat(cancelLog.getPointType()).isEqualTo(PointType.PARTIAL_CANCELLED);
            assertThat(cancelLog.getAmount()).isEqualTo(200L);
        }

        @Test
        @DisplayName("원본 사용 금액과 정확히 같은 금액을 취소하면 통과한다.")
        void cancelLogExactAmount() {
            //given
            PointLog originLog = PointLog.use(1L, 500L, "evt-1");
            ReflectionTestUtils.setField(originLog, "id", 100L);

            //when & then
            assertThatCode(() -> PointLog.rollback(originLog, 500L, "evt-2", true))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("취소 금액이 원본 사용 금액을 초과하면 예외가 발생한다.")
        void cancelLogExceedsOriginal() {
            //given
            PointLog originLog = PointLog.use(1L, 500L, "evt-1");
            ReflectionTestUtils.setField(originLog, "id", 100L);

            //when & then
            assertThatThrownBy(() -> PointLog.rollback(originLog, 600L, "evt-2", true))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("원본 로그가 null이면 예외가 발생한다.")
        void cancelLogNullOrigin() {
            assertThatThrownBy(() -> PointLog.rollback(null, 500L, "evt-2", true))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("원본 로그가 USE 타입이 아니면 예외가 발생한다.")
        void cancelLogNotUseType() {
            //given
            PointLog earnLog = PointLog.earn(1L, 500L, "evt-1");
            ReflectionTestUtils.setField(earnLog, "id", 100L);

            //when & then
            assertThatThrownBy(() -> PointLog.rollback(earnLog, 500L, "evt-2", true))
                    .isInstanceOf(BusinessException.class);
        }

    }
}