package com.programmers.kdt.standby.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.performance.entity.Performance;
import com.programmers.kdt.performance.entity.PerformanceSession;
import com.programmers.kdt.performance.entity.PerformanceSessionId;
import com.programmers.kdt.performance.repository.PerformanceSeatPriceRepository;
import com.programmers.kdt.performance.repository.PerformanceSessionRepository;
import com.programmers.kdt.standby.entity.Standby;
import com.programmers.kdt.standby.entity.StandbyStatus;
import com.programmers.kdt.standby.dto.StandbyListResponse;
import com.programmers.kdt.standby.dto.StandbyRankResponse;
import com.programmers.kdt.standby.event.StandbyCheckResponseEvent;
import com.programmers.kdt.standby.event.StandbyTicketEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.programmers.kdt.standby.exception.StandbyErrorCode;
import com.programmers.kdt.standby.repository.StandbyRepository;
import com.programmers.kdt.ticket.event.StandbyCheckRequestEvent;
import com.programmers.kdt.ticket.repository.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StandbyServiceTest {

    @Mock
    private StandbyRepository standbyRepository;

    @Mock
    private PerformanceSessionRepository performanceSessionRepository;

    @Mock
    private PerformanceSeatPriceRepository performanceSeatPriceRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private StandbyService standbyService;

    private static final Long USER_ID = 1L;
    private static final Long PERFORMANCE_ID = 10L;
    private static final Long SESSION_NUM = 2L;

    private final PerformanceSession session = mock(PerformanceSession.class);

    private PerformanceSessionId sessionId() {
        return new PerformanceSessionId(SESSION_NUM, PERFORMANCE_ID);
    }

    @Nested
    @DisplayName("대기 신청(applyStandby) - 회차 단위 1회 제한")
    class Apply {

        @Test
        @DisplayName("신청 내역이 없고 zone도 유효하면 대기 신청이 저장된다.")
        void applyWhenNotYetApplied() {
            // given
            given(performanceSessionRepository.findById(sessionId()))
                    .willReturn(Optional.of(session));
            given(standbyRepository.existsByUserIdAndPerformanceSession(USER_ID, session))
                    .willReturn(false);
            given(performanceSeatPriceRepository.findZonesByPerformance_PerformanceId(PERFORMANCE_ID))
                    .willReturn(List.of("A", "B", "C"));
            given(standbyRepository.save(any(Standby.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            standbyService.applyStandby(USER_ID, PERFORMANCE_ID, SESSION_NUM, List.of("A", "B", "C"));

            // then
            verify(standbyRepository).save(any(Standby.class));
        }

        @Test
        @DisplayName("이미 같은 회차에 신청했다면 ALREADY_APPLIED 예외가 발생하고 저장되지 않는다.")
        void rejectDuplicateApplyToSameSession() {
            // given
            given(performanceSessionRepository.findById(sessionId()))
                    .willReturn(Optional.of(session));
            given(standbyRepository.existsByUserIdAndPerformanceSession(USER_ID, session))
                    .willReturn(true);

            // when & then
            assertThatThrownBy(() ->
                    standbyService.applyStandby(USER_ID, PERFORMANCE_ID, SESSION_NUM, List.of("A", "B", "C")))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.ALREADY_APPLIED)
                    );
            verify(standbyRepository, never()).save(any(Standby.class));
        }

        @Test
        @DisplayName("같은 회차라면 지망 zone이 달라도 이미 신청했으므로 차단된다.")
        void rejectDuplicateEvenWhenZonesDiffer() {
            // given - 최초 신청은 A/B/C , 전혀 다른 zone(D/E/F)으로 재신청
            given(performanceSessionRepository.findById(sessionId()))
                    .willReturn(Optional.of(session));
            given(standbyRepository.existsByUserIdAndPerformanceSession(USER_ID, session))
                    .willReturn(true);

            // when & then - zone이 달라도 (userId, session) 기준으로 이미 신청 → 차단
            assertThatThrownBy(() ->
                    standbyService.applyStandby(USER_ID, PERFORMANCE_ID, SESSION_NUM, List.of("D", "E", "F")))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.ALREADY_APPLIED)
                    );
            verify(standbyRepository, never()).save(any(Standby.class));
        }
    }

    @Nested
    @DisplayName("대기 신청(applyStandby) - zone 유효성 검증")
    class ZoneValidation {

        @Test
        @DisplayName("요청 zone이 모두 해당 공연이 사용하는 zone이면 저장된다.")
        void applyWithZonesUsedByPerformance() {
            // given - 공연이 사용하는 zone은 A/B/C, 요청은 그 부분집합
            given(performanceSessionRepository.findById(sessionId()))
                    .willReturn(Optional.of(session));
            given(standbyRepository.existsByUserIdAndPerformanceSession(USER_ID, session))
                    .willReturn(false);
            given(performanceSeatPriceRepository.findZonesByPerformance_PerformanceId(PERFORMANCE_ID))
                    .willReturn(List.of("A", "B", "C"));
            given(standbyRepository.save(any(Standby.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            standbyService.applyStandby(USER_ID, PERFORMANCE_ID, SESSION_NUM, List.of("A", "B"));

            // then
            verify(standbyRepository).save(any(Standby.class));
        }

        @Test
        @DisplayName("요청 zone 중 하나라도 해당 공연이 쓰지 않는 값이면 INVALID_ZONE 예외가 발생하고 저장되지 않는다.")
        void rejectZoneNotUsedByPerformance() {
            // given - 공연이 사용하는 zone은 A/B/C 인데 요청에 없는 zone "Z"가 섞임
            given(performanceSessionRepository.findById(sessionId()))
                    .willReturn(Optional.of(session));
            given(standbyRepository.existsByUserIdAndPerformanceSession(USER_ID, session))
                    .willReturn(false);
            given(performanceSeatPriceRepository.findZonesByPerformance_PerformanceId(PERFORMANCE_ID))
                    .willReturn(List.of("A", "B", "C"));

            // when & then
            assertThatThrownBy(() ->
                    standbyService.applyStandby(USER_ID, PERFORMANCE_ID, SESSION_NUM, List.of("A", "Z")))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.INVALID_ZONE)
                    );
            verify(standbyRepository, never()).save(any(Standby.class));
        }
    }

    @Nested
    @DisplayName("대기 신청(applyStandby) - 매진 전제 검증")
    class SoldOutPrecondition {

        @Test
        @DisplayName("요청 zone이 모두 매진이면(잔여 좌석 없음) 대기 신청이 저장된다.")
        void applyWhenAllZonesSoldOut() {
            // given - 유효 zone A/B/C, 잔여 좌석 있는 zone은 없음(빈 목록) → 전부 매진
            given(performanceSessionRepository.findById(sessionId()))
                    .willReturn(Optional.of(session));
            given(standbyRepository.existsByUserIdAndPerformanceSession(USER_ID, session))
                    .willReturn(false);
            given(performanceSeatPriceRepository.findZonesByPerformance_PerformanceId(PERFORMANCE_ID))
                    .willReturn(List.of("A", "B", "C"));
            given(ticketRepository.findAvailableZones(PERFORMANCE_ID, SESSION_NUM, List.of("A", "B")))
                    .willReturn(List.of());
            given(standbyRepository.save(any(Standby.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            standbyService.applyStandby(USER_ID, PERFORMANCE_ID, SESSION_NUM, List.of("A", "B"));

            // then
            verify(standbyRepository).save(any(Standby.class));
        }

        @Test
        @DisplayName("요청 zone 중 하나라도 잔여 좌석이 있으면 ZONE_NOT_SOLD_OUT 예외가 발생하고 저장되지 않는다.")
        void rejectWhenAnyZoneHasAvailableSeats() {
            // given - 유효 zone A/B/C, 그런데 A에는 아직 잔여 좌석이 있음
            given(performanceSessionRepository.findById(sessionId()))
                    .willReturn(Optional.of(session));
            given(standbyRepository.existsByUserIdAndPerformanceSession(USER_ID, session))
                    .willReturn(false);
            given(performanceSeatPriceRepository.findZonesByPerformance_PerformanceId(PERFORMANCE_ID))
                    .willReturn(List.of("A", "B", "C"));
            given(ticketRepository.findAvailableZones(PERFORMANCE_ID, SESSION_NUM, List.of("A", "B")))
                    .willReturn(List.of("A"));

            // when & then
            assertThatThrownBy(() ->
                    standbyService.applyStandby(USER_ID, PERFORMANCE_ID, SESSION_NUM, List.of("A", "B")))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.ZONE_NOT_SOLD_OUT)
                    );
            verify(standbyRepository, never()).save(any(Standby.class));
        }
    }

    @Nested
    @DisplayName("대기자 확인(StandbyCheck) - ticket이 좌석을 대기표로 전환한 직후 이벤트로 호출")
    class StandbyCheckTest {

        private static final Long TICKET_ID = 500L;

        @Test
        @DisplayName("해당 zone 대기자가 있으면 FIFO 선두 1명이 HELD로 바뀌고, ticket에 매칭 결과를 알린 뒤 존재함을 응답한다.")
        void matchesEarliestCandidateAndRespondsExists() {
            // given
            StandbyCheckRequestEvent event =
                    new StandbyCheckRequestEvent(PERFORMANCE_ID, SESSION_NUM, "A", TICKET_ID);

            Standby earliest = mock(Standby.class);
            LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(30);
            Long standbyId = 100L;
            given(earliest.getStandbyId()).willReturn(standbyId);
            given(earliest.getUserId()).willReturn(USER_ID);
            given(earliest.getExpiredAt()).willReturn(expiredAt);

            given(performanceSessionRepository.findById(sessionId()))
                    .willReturn(Optional.of(session));
            given(standbyRepository.findMatchCandidate(session, "A", StandbyStatus.WAITING))
                    .willReturn(Optional.of(earliest));

            // when
            standbyService.StandbyCheck(event);

            // then
            verify(earliest).hold("A", TICKET_ID);
            verify(eventPublisher)
                    .publishEvent(new StandbyTicketEvent(standbyId, TICKET_ID, USER_ID, expiredAt));
            verify(eventPublisher)
                    .publishEvent(new StandbyCheckResponseEvent(TICKET_ID, true));
        }

        @Test
        @DisplayName("해당 zone 대기자가 없으면 매칭도, ticket 알림도 없이 존재하지 않음만 응답한다.")
        void respondsNotExistsWhenNoCandidate() {
            // given
            StandbyCheckRequestEvent event =
                    new StandbyCheckRequestEvent(PERFORMANCE_ID, SESSION_NUM, "A", TICKET_ID);

            given(performanceSessionRepository.findById(sessionId()))
                    .willReturn(Optional.of(session));
            given(standbyRepository.findMatchCandidate(session, "A", StandbyStatus.WAITING))
                    .willReturn(Optional.empty());

            // when
            standbyService.StandbyCheck(event);

            // then
            verify(eventPublisher, never()).publishEvent(any(StandbyTicketEvent.class));
            verify(eventPublisher)
                    .publishEvent(new StandbyCheckResponseEvent(TICKET_ID, false));
        }
    }

    @Nested
    @DisplayName("대기 취소(cancelStandby)")
    class CancelStandby {

        private static final Long STANDBY_ID = 1L;

        @Test
        @DisplayName("WAITING 상태의 본인 신청을 취소하면 cancel()만 호출되고, 재매칭은 시도하지 않는다.")
        void cancelWaitingStandby() {
            // given
            Standby standby = mock(Standby.class);
            given(standbyRepository.findByIdForUpdate(STANDBY_ID)).willReturn(Optional.of(standby));
            given(standby.getUserId()).willReturn(USER_ID);
            given(standby.getStandbyStatus()).willReturn(StandbyStatus.WAITING);

            // when
            standbyService.cancelStandby(STANDBY_ID, USER_ID);

            // then
            verify(standby).cancel();
            verify(standbyRepository, never())
                    .findMatchCandidate(any(), any(), any());
        }

        @Test
        @DisplayName("HELD 상태의 본인 신청을 취소하면 cancel() 후, 매칭됐던 zone에 대한 재매칭을 이벤트로 요청한다.")
        void cancelHeldStandbyTriggersRematch() {
            // given - 취소되는 standby가 ticketId=900을 들고 있었음
            Long ticketId = 900L;
            Standby cancelled = mock(Standby.class);
            given(standbyRepository.findByIdForUpdate(STANDBY_ID)).willReturn(Optional.of(cancelled));
            given(cancelled.getUserId()).willReturn(USER_ID);
            given(cancelled.getStandbyStatus()).willReturn(StandbyStatus.HELD);
            given(cancelled.getMatchedZone()).willReturn("A");
            given(cancelled.getTicketId()).willReturn(ticketId);
            given(cancelled.getPerformanceSession()).willReturn(session);
            given(session.getPerformanceSessionId()).willReturn(sessionId());

            // when
            standbyService.cancelStandby(STANDBY_ID, USER_ID);

            // then - 재매칭은 커밋 이후 이벤트(StandbyCheckRequestEvent)로 위임되고, 취소된 신청의
            // ticketId를 그대로 싣는다. 다음 후보로의 실제 매칭(hold/알림)은 StandbyCheckTest에서 검증함.
            verify(cancelled).cancel();
            verify(eventPublisher)
                    .publishEvent(new StandbyCheckRequestEvent(PERFORMANCE_ID, SESSION_NUM, "A", ticketId));
        }

        @Test
        @DisplayName("존재하지 않는 standbyId면 STANDBY_NOT_FOUND 예외가 발생한다.")
        void rejectWhenStandbyNotFound() {
            // given
            given(standbyRepository.findByIdForUpdate(STANDBY_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> standbyService.cancelStandby(STANDBY_ID, USER_ID))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.STANDBY_NOT_FOUND)
                    );
        }

        @Test
        @DisplayName("본인 신청이 아니면 NOT_STANDBY_OWNER 예외가 발생하고 취소되지 않는다.")
        void rejectWhenNotOwner() {
            // given - 신청자는 USER_ID인데, 다른 사람(999L)이 취소를 시도
            Standby standby = mock(Standby.class);
            given(standbyRepository.findByIdForUpdate(STANDBY_ID)).willReturn(Optional.of(standby));
            given(standby.getUserId()).willReturn(999L);

            // when & then
            assertThatThrownBy(() -> standbyService.cancelStandby(STANDBY_ID, USER_ID))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.NOT_STANDBY_OWNER)
                    );
            verify(standby, never()).cancel();
        }
    }

    @Nested
    @DisplayName("지망 zone 부분 취소(cancelZone)")
    class CancelZone {

        private static final Long STANDBY_ID = 1L;

        @Test
        @DisplayName("매칭 안 된 zone을 취소하면 엔티티에 위임만 하고, 재매칭은 시도하지 않는다.")
        void delegatesToEntityCancelZoneWithoutRematch() {
            // given - getMatchedZone()이 null이라 취소하려는 "B"와 다름(매칭된 zone이 아님)
            Standby standby = mock(Standby.class);
            given(standbyRepository.findByIdForUpdate(STANDBY_ID)).willReturn(Optional.of(standby));
            given(standby.getUserId()).willReturn(USER_ID);

            // when
            standbyService.cancelZone(STANDBY_ID, USER_ID, "B");

            // then
            verify(standby).cancelZone("B");
            verify(standbyRepository, never())
                    .findMatchCandidate(any(), any(), any());
        }

        @Test
        @DisplayName("취소한 zone이 매칭돼있던(HELD) zone이면, 같은 zone에 대한 재매칭을 이벤트로 요청한다.")
        void cancelMatchedZoneTriggersRematch() {
            // given - 취소되는 standby가 ticketId=901을 들고 있었음
            Long ticketId = 901L;
            Standby standby = mock(Standby.class);
            given(standbyRepository.findByIdForUpdate(STANDBY_ID)).willReturn(Optional.of(standby));
            given(standby.getUserId()).willReturn(USER_ID);
            given(standby.getMatchedZone()).willReturn("B");
            given(standby.getTicketId()).willReturn(ticketId);
            given(standby.getPerformanceSession()).willReturn(session);
            given(session.getPerformanceSessionId()).willReturn(sessionId());

            // when
            standbyService.cancelZone(STANDBY_ID, USER_ID, "B");

            // then - 재매칭은 커밋 이후 이벤트(StandbyCheckRequestEvent)로 위임되고, 취소된 신청의
            // ticketId를 그대로 싣는다. 다음 후보로의 실제 매칭(hold/알림)은 StandbyCheckTest에서 검증함.
            verify(standby).cancelZone("B");
            verify(eventPublisher)
                    .publishEvent(new StandbyCheckRequestEvent(PERFORMANCE_ID, SESSION_NUM, "B", ticketId));
        }

        @Test
        @DisplayName("본인 신청이 아니면 NOT_STANDBY_OWNER 예외가 발생하고 취소되지 않는다.")
        void rejectWhenNotOwner() {
            // given
            Standby standby = mock(Standby.class);
            given(standbyRepository.findByIdForUpdate(STANDBY_ID)).willReturn(Optional.of(standby));
            given(standby.getUserId()).willReturn(999L);

            // when & then
            assertThatThrownBy(() -> standbyService.cancelZone(STANDBY_ID, USER_ID, "B"))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.NOT_STANDBY_OWNER)
                    );
            verify(standby, never()).cancelZone(any());
        }
    }

    @Nested
    @DisplayName("대기 순위 조회(getStandbyRank)")
    class GetStandbyRank {

        private static final Long STANDBY_ID = 1L;
        private final LocalDateTime reservedAt = LocalDateTime.now();

        @Test
        @DisplayName("지망 zone이 1개면, 그 zone에서 나보다 먼저 신청한 인원 수 + 1이 순위로 반환된다.")
        void returnsRankForSingleZone() {
            // given
            Standby standby = mock(Standby.class);
            given(standbyRepository.findById(STANDBY_ID)).willReturn(Optional.of(standby));
            given(standby.getUserId()).willReturn(USER_ID);
            given(standby.getStandbyId()).willReturn(STANDBY_ID);
            given(standby.canViewRank()).willReturn(true);
            given(standby.getZone1()).willReturn("A");
            given(standby.getZone2()).willReturn(null);
            given(standby.getZone3()).willReturn(null);
            given(standby.getReservedAt()).willReturn(reservedAt);
            given(standby.getPerformanceSession()).willReturn(session);
            given(standbyRepository.countRankCount(session, "A", StandbyStatus.WAITING, reservedAt))
                    .willReturn(2L);

            // when
            StandbyRankResponse response = standbyService.getStandbyRank(STANDBY_ID, USER_ID);

            // then
            assertThat(response.standbyId()).isEqualTo(STANDBY_ID);
            assertThat(response.zoneRanks())
                    .containsExactly(new StandbyRankResponse.ZoneRank("A", 3L, false, null));
        }

        @Test
        @DisplayName("지망 zone이 여러 개면, zone별로 각각의 순위가 리스트로 반환된다.")
        void returnsRankForEachZone() {
            // given
            Standby standby = mock(Standby.class);
            given(standbyRepository.findById(STANDBY_ID)).willReturn(Optional.of(standby));
            given(standby.getUserId()).willReturn(USER_ID);
            given(standby.getStandbyId()).willReturn(STANDBY_ID);
            given(standby.canViewRank()).willReturn(true);
            given(standby.getZone1()).willReturn("A");
            given(standby.getZone2()).willReturn("B");
            given(standby.getZone3()).willReturn(null);
            given(standby.getReservedAt()).willReturn(reservedAt);
            given(standby.getPerformanceSession()).willReturn(session);
            given(standbyRepository.countRankCount(session, "A", StandbyStatus.WAITING, reservedAt))
                    .willReturn(0L);
            given(standbyRepository.countRankCount(session, "B", StandbyStatus.WAITING, reservedAt))
                    .willReturn(4L);

            // when
            StandbyRankResponse response = standbyService.getStandbyRank(STANDBY_ID, USER_ID);

            // then
            assertThat(response.zoneRanks())
                    .containsExactly(
                            new StandbyRankResponse.ZoneRank("A", 1L, false, null),
                            new StandbyRankResponse.ZoneRank("B", 5L, false, null)
                    );
        }

        @Test
        @DisplayName("HELD 상태의 경우에도, 나머지 대기상태 정상 순위로 반환.")
        void returnsGetHeldTest() {
            // given - A매칭(HELD), B는 대기(3번째)
            Standby standby = mock(Standby.class);
            given(standbyRepository.findById(STANDBY_ID)).willReturn(Optional.of(standby));
            given(standby.getUserId()).willReturn(USER_ID);
            given(standby.getStandbyId()).willReturn(STANDBY_ID);
            given(standby.canViewRank()).willReturn(true);
            given(standby.getMatchedZone()).willReturn("A");
            given(standby.getZone1()).willReturn("A");
            given(standby.getZone2()).willReturn("B");
            given(standby.getZone3()).willReturn(null);
            given(standby.getReservedAt()).willReturn(reservedAt);
            given(standby.getPerformanceSession()).willReturn(session);
            given(standby.getTicketId()).willReturn(999L);
            given(standbyRepository.countRankCount(session, "B", StandbyStatus.WAITING, reservedAt))
                    .willReturn(2L);

            // when
            StandbyRankResponse response = standbyService.getStandbyRank(STANDBY_ID, USER_ID);

            // then
            assertThat(response.zoneRanks())
                    .containsExactly(
                            new StandbyRankResponse.ZoneRank("A", 0L, true, 999L),
                            new StandbyRankResponse.ZoneRank("B", 3L, false, null)
                    );
            verify(standbyRepository, never())
                    .countRankCount(eq(session), eq("A"), any(), any());
        }

        @Test
        @DisplayName("본인 신청이 아니면 NOT_STANDBY_OWNER 예외가 발생한다.")
        void rejectWhenNotOwner() {
            // given
            Standby standby = mock(Standby.class);
            given(standbyRepository.findById(STANDBY_ID)).willReturn(Optional.of(standby));
            given(standby.getUserId()).willReturn(999L);

            // when & then
            assertThatThrownBy(() -> standbyService.getStandbyRank(STANDBY_ID, USER_ID))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.NOT_STANDBY_OWNER)
                    );
        }

        @Test
        @DisplayName("WAITING 상태가 아니면(예: 취소됨) CANNOT_VIEW_RANK 예외가 발생한다.")
        void rejectWhenNotWaiting() {
            // given
            Standby standby = mock(Standby.class);
            given(standbyRepository.findById(STANDBY_ID)).willReturn(Optional.of(standby));
            given(standby.getUserId()).willReturn(USER_ID);
            given(standby.canViewRank()).willReturn(false);

            // when & then
            assertThatThrownBy(() -> standbyService.getStandbyRank(STANDBY_ID, USER_ID))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(StandbyErrorCode.CANNOT_VIEW_RANK)
                    );
        }
    }

    @Nested
    @DisplayName("내 대기 목록 전체 조회(getStandby)")
    class GetStandby {

        private static final Long STANDBY_ID = 1L;

        @Test
        @DisplayName("내 대기 신청이 여러 건이면 각각 공연/회차/상태 정보로 매핑되어 반환된다.")
        void returnsMappedListForUser() {
            // given - 실제 도메인 객체(공연 → 회차 → 대기신청)를 팩토리 메서드로 조립, ID만 생성된 값으로 주입
            Performance performance = Performance.createPerformance(
                    "보노보노 콘서트", "보노보노의 콘서트입니다", 120L,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(11), null,
                    777L, 1L, null);
            LocalDateTime startAt = LocalDateTime.now().plusDays(10);
            PerformanceSession realSession = PerformanceSession.create(sessionId(), performance, "보노보노", startAt);

            Standby standby = Standby.apply(USER_ID, realSession, List.of("A", "B"));
            standby.hold("A", 999L);
            ReflectionTestUtils.setField(standby, "standbyId", STANDBY_ID);

            given(standbyRepository.findByUserIdOrderByReservedAtDesc(USER_ID))
                    .willReturn(List.of(standby));

            // when
            List<StandbyListResponse> responses = standbyService.getStandby(USER_ID);

            // then
            assertThat(responses).containsExactly(new StandbyListResponse(
                    STANDBY_ID, PERFORMANCE_ID, SESSION_NUM, "보노보노 콘서트", startAt,
                    List.of("A", "B"), "A", 999L, StandbyStatus.HELD.name(),
                    standby.getReservedAt(), standby.getExpiredAt()
            ));
        }

        @Test
        @DisplayName("신청 내역이 없으면 빈 목록을 반환한다.")
        void returnsEmptyListWhenNoStandby() {
            // given
            given(standbyRepository.findByUserIdOrderByReservedAtDesc(USER_ID))
                    .willReturn(List.of());

            // when
            List<StandbyListResponse> responses = standbyService.getStandby(USER_ID);

            // then
            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("결제 기한 만료에 대한 테스트")
    class ExpireHeldStandbys {

        @Test
        @DisplayName("매칭 zone만 취소되고 이후 남은 지망 zone은 WAITING으로 유지된 채 같은 zone으로 다음 재매칭 시도")
        void expiredStandbyKeepsOtherZonesWaiting() {
            // given - VIP/S/R 지망 중 VIP로 매칭(HELD)됐고 결제 기한(expiredAt)이 지남
            Standby expired = Standby.apply(USER_ID, session, List.of("VIP", "S", "R"));
            expired.hold("VIP", 1L);
            given(standbyRepository.findAllByStandbyStatusAndExpiredAtLessThanEqual(eq(StandbyStatus.HELD), any()))
                    .willReturn(List.of(expired));
            given(standbyRepository.findMatchCandidate(session, "VIP", StandbyStatus.WAITING))
                    .willReturn(Optional.empty());

            // when
            int count = standbyService.expireHeldStandbys();

            // then - VIP 지망만 사라지고 S/R은 그대로 남아 WAITING으로 복귀 (매칭된 zone만취소)
            assertThat(expired.getZone1()).isNull();
            assertThat(expired.getZone2()).isEqualTo("S");
            assertThat(expired.getZone3()).isEqualTo("R");
            assertThat(expired.getStandbyStatus()).isEqualTo(StandbyStatus.WAITING);
            assertThat(expired.getTicketId()).isNull();
            verify(standbyRepository).findMatchCandidate(session, "VIP", StandbyStatus.WAITING);
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("매칭됐던 zone이 유일한 지망이었다면 전체 취소(CANCELLED)로 전환")
        void expiredStandbyWithOnlyMatchedZoneCancelsWhole() {
            // given - VIP 하나만 지망했고 그게 매칭(HELD)된 상태에서 결제 기한이 지남
            Standby expired = Standby.apply(USER_ID, session, List.of("VIP"));
            expired.hold("VIP", 1L);
            given(standbyRepository.findAllByStandbyStatusAndExpiredAtLessThanEqual(eq(StandbyStatus.HELD), any()))
                    .willReturn(List.of(expired));
            given(standbyRepository.findMatchCandidate(session, "VIP", StandbyStatus.WAITING))
                    .willReturn(Optional.empty());

            // when
            standbyService.expireHeldStandbys();

            // then
            assertThat(expired.getStandbyStatus()).isEqualTo(StandbyStatus.CANCELLED);
        }

        @Test
        @DisplayName("만료 후 재매칭되면, 만료된 신청이 들고 있던 ticketId를 그대로 이어받아 ticket에 알림")
        void expiredStandbyPropagatesTicketIdToNextCandidate() {
            // given - 만료되는 standby가 ticketId=1을 들고 있었음
            Long ticketId = 1L;
            Standby expired = Standby.apply(USER_ID, session, List.of("VIP"));
            expired.hold("VIP", ticketId);
            given(standbyRepository.findAllByStandbyStatusAndExpiredAtLessThanEqual(eq(StandbyStatus.HELD), any()))
                    .willReturn(List.of(expired));

            Standby next = mock(Standby.class);
            LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(30);
            Long nextStandbyId = 200L;
            given(next.getStandbyId()).willReturn(nextStandbyId);
            given(next.getUserId()).willReturn(2L);
            given(next.getExpiredAt()).willReturn(expiredAt);
            given(standbyRepository.findMatchCandidate(session, "VIP", StandbyStatus.WAITING))
                    .willReturn(Optional.of(next));

            // when
            standbyService.expireHeldStandbys();

            // then - 새로 매칭된 next는 같은 ticketId로 hold되고, 그 ticketId 그대로 이벤트가 발행된다.
            verify(next).hold("VIP", ticketId);
            verify(eventPublisher)
                    .publishEvent(new StandbyTicketEvent(nextStandbyId, ticketId, 2L, expiredAt));
        }
    }
}
