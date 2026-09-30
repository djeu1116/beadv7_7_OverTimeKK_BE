package com.programmers.kdt.standby.domain.entity;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.performance.domain.entity.PerformanceSession;
import com.programmers.kdt.standby.domain.exception.StandbyErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

public class StandbyTest {

    private final PerformanceSession session = mock(PerformanceSession.class);

    @Nested
    @DisplayName("대기 신청(apply)")
    class Apply {

        @Test
        @DisplayName("지망 zone 3개로 신청하면 WAITING 상태로 생성되고 각 지망이 순서대로 담긴다.")
        void applyWithThreeZones() {
            // given
            Long userId = 1L;
            List<String> zones = List.of("A", "B", "C");

            // when
            Standby standby = Standby.apply(userId, session, zones);

            // then
            assertThat(standby.getUserId()).isEqualTo(userId);
            assertThat(standby.getPerformanceSession()).isEqualTo(session);
            assertThat(standby.getZone1()).isEqualTo("A");
            assertThat(standby.getZone2()).isEqualTo("B");
            assertThat(standby.getZone3()).isEqualTo("C");
            assertThat(standby.getStandbyStatus()).isEqualTo(StandbyStatus.WAITING);
            assertThat(standby.getReservedAt()).isNotNull();
        }

        @Test
        @DisplayName("지망 zone 1개로 신청하면 zone1만 채워지고 나머지는 null이다.")
        void applyWithOneZone() {
            // when
            Standby standby = Standby.apply(1L, session, List.of("A"));

            // then (null이 아닌 다른값으로 프론트에게 전달예정)
            assertThat(standby.getZone1()).isEqualTo("A");
            assertThat(standby.getZone2()).isNull();
            assertThat(standby.getZone3()).isNull();
            assertThat(standby.getStandbyStatus()).isEqualTo(StandbyStatus.WAITING);
        }

        @Test
        @DisplayName("지망 zone 2개로 신청하면 zone3만 null이다.")
        void applyWithTwoZones() {
            // when
            Standby standby = Standby.apply(1L, session, List.of("A", "B"));

            // then
            assertThat(standby.getZone1()).isEqualTo("A");
            assertThat(standby.getZone2()).isEqualTo("B");
            assertThat(standby.getZone3()).isNull();
        }

        @Test
        @DisplayName("지망 zone 목록이 null이면 INVALID_ZONE_COUNT 예외가 발생한다.")
        void applyWithNullZones() {
            assertThatThrownBy(() -> Standby.apply(1L, session, null))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.INVALID_ZONE_COUNT)
                    );
        }

        @Test
        @DisplayName("지망 zone 목록이 비어있으면 INVALID_ZONE_COUNT 예외가 발생한다.")
        void applyWithEmptyZones() {
            assertThatThrownBy(() -> Standby.apply(1L, session, List.of()))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.INVALID_ZONE_COUNT)
                    );
        }

        @Test
        @DisplayName("지망 zone이 3개를 초과하면 INVALID_ZONE_COUNT 예외가 발생한다.")
        void applyWithTooManyZones() {
            assertThatThrownBy(() -> Standby.apply(1L, session, List.of("A", "B", "C", "D")))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.INVALID_ZONE_COUNT)
                    );
        }

        @Test
        @DisplayName("중복된 지망 zone이 있으면 DUPLICATE_ZONE 예외가 발생한다.")
        void applyWithDuplicateZones() {
            assertThatThrownBy(() -> Standby.apply(1L, session, List.of("A", "A", "B")))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.DUPLICATE_ZONE)
                    );
        }

        @Test
        @DisplayName("지망 zone 목록에 null 항목이 포함되면 DUPLICATE_ZONE 예외가 발생한다.")
        void applyWithNullZoneElement() {
            // 현재 validateZones는 null 항목을 distinct 후 개수 불일치로 걸러낸다.
            assertThatThrownBy(() -> Standby.apply(1L, session, Arrays.asList("A", null)))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.DUPLICATE_ZONE)
                    );
        }
    }

    @Nested
    @DisplayName("매칭 확정(hold)")
    class Hold {

        @Test
        @DisplayName("매칭된 zone이 zone2 지망이면 slot이 ZONE2로, 상태는 HELD로 바뀐다.")
        void holdSetsSlotAndStatus() {
            // given
            Standby standby = Standby.apply(1L, session, List.of("A", "B", "C"));

            // when
            standby.hold("B", null);

            // then
            assertThat(standby.getSlot()).isEqualTo(Slot.ZONE2);
            assertThat(standby.getStandbyStatus()).isEqualTo(StandbyStatus.HELD);
            assertThat(standby.getExpiredAt()).isNotNull();
        }

        @Test
        @DisplayName("매칭하려는 zone이 지망 목록(zone1/zone2/zone3)에 없으면 예외가 발생한다.")
        void holdWithZoneNotRequested() {
            // given
            Standby standby = Standby.apply(1L, session, List.of("A"));

            // when & then
            assertThatThrownBy(() -> standby.hold("Z", null))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("ticketId를 함께 넘겨 매칭하면 ticketId가 저장된다.")
        void holdWithTicketIdStoresTicketId() {
            // given- 대기등록()
            Standby standby = Standby.apply(1L, session, List.of("A"));

            // when
            standby.hold("A", 42L);

            // then
            assertThat(standby.getTicketId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("ticketId로 null을 넘겨 매칭하면 ticketId는 null이다.")
        void holdWithoutTicketIdLeavesTicketIdNull() {
            // given
            Standby standby = Standby.apply(1L, session, List.of("A"));

            // when
            standby.hold("A", null);

            // then
            assertThat(standby.getTicketId()).isNull();
        }
    }

    @Nested
    @DisplayName("매칭 대상 zone 조회(getMatchedZone)")
    class MatchedZone {

        @Test
        @DisplayName("매칭 전(WAITING)이면 null을 반환한다.")
        void nullBeforeHold() {
            // given
            Standby standby = Standby.apply(1L, session, List.of("A", "B"));

            // then
            assertThat(standby.getMatchedZone()).isNull();
        }

        @Test
        @DisplayName("zone3로 매칭됐으면 zone3 값을 반환한다.")
        void returnsMatchedZoneValue() {
            // given
            Standby standby = Standby.apply(1L, session, List.of("A", "B", "C"));

            // when
            standby.hold("C", null);

            // then
            assertThat(standby.getMatchedZone()).isEqualTo("C");
        }
    }

    @Nested
    @DisplayName("대기 취소(cancel)")
    class Cancel {

        @Test
        @DisplayName("WAITING 상태를 취소하면 CANCELLED로 바뀐다.")
        void cancelFromWaiting() {
            // given
            Standby standby = Standby.apply(1L, session, List.of("A"));

            // when
            standby.cancel();

            // then
            assertThat(standby.getStandbyStatus()).isEqualTo(StandbyStatus.CANCELLED);
        }

        @Test
        @DisplayName("HELD 상태(매칭된 상태)를 취소하면 CANCELLED로 바뀐다.")
        void cancelFromHeld() {
            // given
            Standby standby = Standby.apply(1L, session, List.of("A"));
            standby.hold("A", null);

            // when
            standby.cancel();

            // then
            assertThat(standby.getStandbyStatus()).isEqualTo(StandbyStatus.CANCELLED);
        }

        @Test
        @DisplayName("이미 CANCELLED 상태면 다시 취소해도 예외 없이 CANCELLED를 유지(멱등).")
        void cancelAlreadyCancelledIsIdempotent() {
            // given
            Standby standby = Standby.apply(1L, session, List.of("A"));
            standby.cancel();

            // when & then
            assertThatCode(standby::cancel).doesNotThrowAnyException();
            assertThat(standby.getStandbyStatus()).isEqualTo(StandbyStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("지망 zone 부분 취소(cancelZone)")
    class CancelZone {

        @Test
        @DisplayName("여러 zone 중 하나만 취소하면 그 zone만 사라지고 나머지는 유지, 상태는 그대로다.")
        void cancelOneOfMultipleZones() {
            // given
            Standby standby = Standby.apply(1L, session, List.of("A", "B", "C"));

            // when
            standby.cancelZone("B");

            // then
            assertThat(standby.getZone1()).isEqualTo("A");
            assertThat(standby.getZone2()).isNull();
            assertThat(standby.getZone3()).isEqualTo("C");
            assertThat(standby.getStandbyStatus()).isEqualTo(StandbyStatus.WAITING);
        }

        @Test
        @DisplayName("남은 마지막 zone까지 취소하면 전체 취소(CANCELLED)로 전환된다.")
        void cancelLastRemainingZoneCancelsWhole() {
            // given
            Standby standby = Standby.apply(1L, session, List.of("A"));

            // when
            standby.cancelZone("A");

            // then
            assertThat(standby.getZone1()).isNull();
            assertThat(standby.getStandbyStatus()).isEqualTo(StandbyStatus.CANCELLED);
        }

        @Test
        @DisplayName("HELD 상태에서 매칭 안 된(backup) zone을 취소해도 매칭 상태(slot, HELD)는 그대로 유지된다.")
        void cancelNonMatchedZoneKeepsHeldState() {
            // given - A로 매칭(HELD), B/C는 아직 후보로 남아있는 상태
            Standby standby = Standby.apply(1L, session, List.of("A", "B", "C"));
            standby.hold("A", null);

            // when
            standby.cancelZone("C");

            // then
            assertThat(standby.getZone3()).isNull();
            assertThat(standby.getStandbyStatus()).isEqualTo(StandbyStatus.HELD);
            assertThat(standby.getSlot()).isEqualTo(Slot.ZONE1);
        }

        @Test
        @DisplayName("HELD 상태에서 매칭된(slot) zone을 취소하면, 남은 zone에 대해 WAITING으로 되돌아간다.")
        void cancelMatchedZoneRevertsToWaiting() {
            // given - B로 매칭(HELD, ticketId=42), A/C는 아직 후보
            Standby standby = Standby.apply(1L, session, List.of("A", "B", "C"));
            standby.hold("B", 42L);

            // when
            standby.cancelZone("B");

            // then
            assertThat(standby.getZone2()).isNull();
            assertThat(standby.getZone1()).isEqualTo("A");
            assertThat(standby.getZone3()).isEqualTo("C");
            assertThat(standby.getStandbyStatus()).isEqualTo(StandbyStatus.WAITING);
            assertThat(standby.getSlot()).isNull();
            assertThat(standby.getExpiredAt()).isNull();
            assertThat(standby.getTicketId()).isNull();
            assertThat(standby.getMatchedZone()).isNull();
        }

        @Test
        @DisplayName("HELD 상태에서 매칭된 zone이 유일하게 남은 zone이었다면, 취소 시 바로 전체 취소(CANCELLED)된다.")
        void cancelMatchedZoneAsLastRemainingCancelsWhole() {
            // given - 지망 zone이 A 하나뿐이고, 그게 매칭(HELD)된 상태
            Standby standby = Standby.apply(1L, session, List.of("A"));
            standby.hold("A", null);

            // when
            standby.cancelZone("A");

            // then
            assertThat(standby.getZone1()).isNull();
            assertThat(standby.getStandbyStatus()).isEqualTo(StandbyStatus.CANCELLED);
            assertThat(standby.getSlot()).isNull();
            assertThat(standby.getExpiredAt()).isNull();
        }

        @Test
        @DisplayName("지망하지 않은 zone을 취소하려 하면 ZONE_NOT_IN_STANDBY 예외가 발생한다.")
        void rejectCancelZoneNotRequested() {
            // given
            Standby standby = Standby.apply(1L, session, List.of("A"));

            // when & then
            assertThatThrownBy(() -> standby.cancelZone("Z"))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.ZONE_NOT_IN_STANDBY)
                    );
        }

        @Test
        @DisplayName("이미 CANCELLED 상태면 zone 취소를 시도해도 예외 없이 그대로 CANCELLED다(멱등).")
        void cancelZoneOnAlreadyCancelledIsIdempotent() {
            // given
            Standby standby = Standby.apply(1L, session, List.of("A"));
            standby.cancel();

            // when & then
            assertThatCode(() -> standby.cancelZone("A")).doesNotThrowAnyException();
            assertThat(standby.getStandbyStatus()).isEqualTo(StandbyStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("순위 조회 가능 여부(canViewRank)")
    class CanViewRank {

        @Test
        @DisplayName("WAITING 상태면 조회 가능하다.")
        void trueWhenWaiting() {
            Standby standby = Standby.apply(1L, session, List.of("A"));

            assertThat(standby.canViewRank()).isTrue();
        }

        @Test
        @DisplayName("HELD 상태면 조회 가능하다.")
        void trueWhenHeld() {
            Standby standby = Standby.apply(1L, session, List.of("A"));
            standby.hold("A", null);

            assertThat(standby.canViewRank()).isTrue();
        }

        @Test
        @DisplayName("CANCELLED 상태면 조회 불가능하다.")
        void falseWhenCancelled() {
            Standby standby = Standby.apply(1L, session, List.of("A"));
            standby.cancel();

            assertThat(standby.canViewRank()).isFalse();
        }
    }
}
