package com.programmers.kdt.payment.scheduler;


import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.client.point.EndedPerformanceClient;
import com.programmers.kdt.payment.client.point.EndedTicket;
import com.programmers.kdt.payment.dto.PointEarnTarget;
import com.programmers.kdt.payment.exception.PointErrorCode;
import com.programmers.kdt.payment.client.order.PointEarnTargetClient;
import com.programmers.kdt.payment.service.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointEarnSchedulerTest {

    @Mock
    private EndedPerformanceClient endedPerformanceClient;

    @Mock
    private PointEarnTargetClient pointEarnTargetClient;

    @Mock
    private PointService pointService;

    private PointEarnScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PointEarnScheduler(endedPerformanceClient, pointEarnTargetClient, pointService);
    }

    @Test
    @DisplayName("종료된 공연 티켓이 없으면 이후 로직은 실행되지 않는다.")
    void noEndedTickets_doesNothing() {
        when(endedPerformanceClient.findEndedTickets(any(), any())).thenReturn(List.of());

        scheduler.earnPointsForEndedPerformances();

        verifyNoInteractions(pointEarnTargetClient);
        verifyNoInteractions(pointService);
    }

    @Test
    @DisplayName("하루 전 날짜의 티켓들을 조회한다.")
    void queriesYesterdayDate() {
        when(endedPerformanceClient.findEndedTickets(any(), any())).thenReturn(List.of());

        scheduler.earnPointsForEndedPerformances();

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(endedPerformanceClient).findEndedTickets(fromCaptor.capture(), toCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo(LocalDate.now().minusDays(1));
        assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.now().minusDays(3));
    }

    @Test
    @DisplayName("정산 대상이 있으면 티켓 가격의 1%를 적립하고, eventId는 티켓 단위로 만든다.")
    void earnSuccess_computesOnePercentPerTicket() {
        when(endedPerformanceClient.findEndedTickets(any(), any()))
                .thenReturn(List.of(new EndedTicket(1L, 100L), new EndedTicket(1L, 101L)));
        when(pointEarnTargetClient.findEarnTargets(anyList()))
                .thenReturn(List.of(
                        new PointEarnTarget(10L, 100L, 50_000L),
                        new PointEarnTarget(20L, 101L, 30_000L)
                ));

        scheduler.earnPointsForEndedPerformances();

        verify(pointService).earnPoint(10L, 500L, "TICKET:100:POINT_EARN");
        verify(pointService).earnPoint(20L, 300L, "TICKET:101:POINT_EARN");
    }

    @Test
    @DisplayName("적립액이 0원 이하로 계산되면 그 티켓은 건너뛴다.")
    void earnAmountZeroOrLess_skipped() {
        when(endedPerformanceClient.findEndedTickets(any(), any()))
                .thenReturn(List.of(new EndedTicket(1L, 100L)));
        when(pointEarnTargetClient.findEarnTargets(anyList()))
                .thenReturn(List.of(new PointEarnTarget(10L, 100L, 49L)));

        scheduler.earnPointsForEndedPerformances();

        verifyNoInteractions(pointService);
    }

    @Test
    @DisplayName("from과 to가 같아도 가드에 걸리지 않고 정상 적립된다.")
    void sameFromTo_doesNotTriggerGuard() {
        LocalDate from = LocalDate.of(2026, 8, 4);
        LocalDate to = LocalDate.of(2026, 8, 4);

        when(endedPerformanceClient.findEndedTickets(from, to))
                .thenReturn(List.of(new EndedTicket(1L, 100L)));
        when(pointEarnTargetClient.findEarnTargets(anyList()))
                .thenReturn(List.of(new PointEarnTarget(10L, 100L, 50_000L)));

        assertThatCode(() -> scheduler.earnPointsForEndedPerformances(from, to))
                .doesNotThrowAnyException();

        verify(pointService).earnPoint(10L, 500L, "TICKET:100:POINT_EARN");
    }

    @Test
    @DisplayName("일부 티켓 적립이 실패해도 나머지 티켓은 계속 처리된다.")
    void partialFailure_continuesProcessingOthers() {
        when(endedPerformanceClient.findEndedTickets(any(), any()))
                .thenReturn(List.of(new EndedTicket(1L, 100L), new EndedTicket(1L, 101L)));
        when(pointEarnTargetClient.findEarnTargets(anyList()))
                .thenReturn(List.of(
                        new PointEarnTarget(10L, 100L, 50_000L),
                        new PointEarnTarget(20L, 101L, 30_000L)
                ));
        doThrow(new RuntimeException("동시성 충돌"))
                .when(pointService).earnPoint(10L, 500L, "TICKET:100:POINT_EARN");

        scheduler.earnPointsForEndedPerformances();

        verify(pointService).earnPoint(10L, 500L, "TICKET:100:POINT_EARN");
        verify(pointService).earnPoint(20L, 300L, "TICKET:101:POINT_EARN"); // 첫 번째가 실패해도 두 번째는 호출됨
    }

    @Test
    @DisplayName("종료된 티켓은 있지만 완료된 주문에 속한 티켓이 없으면 적립도 없다.")
    void noMatchingOrderItems_noEarn() {
        when(endedPerformanceClient.findEndedTickets(any(), any()))
                .thenReturn(List.of(new EndedTicket(1L, 100L)));
        when(pointEarnTargetClient.findEarnTargets(anyList()))
                .thenReturn(List.of());

        scheduler.earnPointsForEndedPerformances();

        verifyNoInteractions(pointService);
    }

    @Test
    @DisplayName("동시성 충돌이 3번 연속 발생하면 재시도를 소진하고 그 티켓만 실패 처리한다.")
    void concurrentModification_exhaustsRetries() {
        when(endedPerformanceClient.findEndedTickets(any(), any()))
                .thenReturn(List.of(new EndedTicket(1L, 100L)));
        when(pointEarnTargetClient.findEarnTargets(anyList()))
                .thenReturn(List.of(new PointEarnTarget(10L, 100L, 50_000L)));
        doThrow(new BusinessException(PointErrorCode.POINT_CONCURRENT_MODIFICATION))
                .when(pointService).earnPoint(10L, 500L, "TICKET:100:POINT_EARN");

        scheduler.earnPointsForEndedPerformances();

        verify(pointService, times(3)).earnPoint(10L, 500L, "TICKET:100:POINT_EARN");
    }

    @Test
    @DisplayName("동시성 충돌이 재시도 중 해소되면 그 이후로는 재시도하지 않는다.")
    void concurrentModification_succeedsOnRetry() {
        when(endedPerformanceClient.findEndedTickets(any(), any()))
                .thenReturn(List.of(new EndedTicket(1L, 100L)));
        when(pointEarnTargetClient.findEarnTargets(anyList()))
                .thenReturn(List.of(new PointEarnTarget(10L, 100L, 50_000L)));
        doThrow(new BusinessException(PointErrorCode.POINT_CONCURRENT_MODIFICATION))
                .doNothing()
                .when(pointService).earnPoint(10L, 500L, "TICKET:100:POINT_EARN");

        scheduler.earnPointsForEndedPerformances();

        verify(pointService, times(2)).earnPoint(10L, 500L, "TICKET:100:POINT_EARN");
    }

    @Test
    @DisplayName("종료 티켓 조회(REST) 자체가 실패해도 예외가 밖으로 새지 않는다.")
    void findEndedTicketsFails_doesNotPropagate() {
        when(endedPerformanceClient.findEndedTickets(any(), any()))
                .thenThrow(new RuntimeException("performance-service 응답 없음"));

        assertThatCode(() -> scheduler.earnPointsForEndedPerformances())
                .doesNotThrowAnyException();

        verifyNoInteractions(pointEarnTargetClient);
        verifyNoInteractions(pointService);
    }

    @Test
    @DisplayName("정산 대상 조회(DB) 자체가 실패해도 예외가 밖으로 새지 않는다.")
    void findEarnTargetsFails_doesNotPropagate() {
        when(endedPerformanceClient.findEndedTickets(any(), any()))
                .thenReturn(List.of(new EndedTicket(1L, 100L)));
        when(pointEarnTargetClient.findEarnTargets(anyList()))
                .thenThrow(new RuntimeException("DB 오류"));

        assertThatCode(() -> scheduler.earnPointsForEndedPerformances())
                .doesNotThrowAnyException();

        verifyNoInteractions(pointService);
    }

    @Test
    @DisplayName("관리자가 잘못된 날짜 범위를 입력하면 예외가 발생한다.")
    void invalidDateRange_throwsException() {
        LocalDate from = LocalDate.of(2026, 8, 4);
        LocalDate to = LocalDate.of(2026, 8, 2);

        assertThatThrownBy(() -> scheduler.earnPointsForEndedPerformances(from, to))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PointErrorCode.INVALID_DATE_RANGE);
    }



    @Test
    @DisplayName("특정 날짜 범위를 지정해서 수동으로 재처리할 수 있다.")
    void manualReplayForSpecificDate() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 2);
        when(endedPerformanceClient.findEndedTickets(from, to))
                .thenReturn(List.of(new EndedTicket(1L, 100L)));
        when(pointEarnTargetClient.findEarnTargets(anyList()))
                .thenReturn(List.of(new PointEarnTarget(10L, 100L, 50_000L)));

        scheduler.earnPointsForEndedPerformances(from, to);

        verify(endedPerformanceClient).findEndedTickets(from, to);
        verify(pointService).earnPoint(10L, 500L, "TICKET:100:POINT_EARN");
    }
}