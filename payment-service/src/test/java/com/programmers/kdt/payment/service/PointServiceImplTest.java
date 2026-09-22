package com.programmers.kdt.payment.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.entity.Point;
import com.programmers.kdt.payment.entity.PointLog;
import com.programmers.kdt.payment.entity.PointType;
import com.programmers.kdt.payment.exception.PointErrorCode;
import com.programmers.kdt.payment.repository.PointLogRepository;
import com.programmers.kdt.payment.repository.PointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class PointServiceImplTest {

    @Mock
    private PointRepository pointRepository;

    @Mock
    private PointLogRepository pointLogRepository;

    private PointService pointService;

    @BeforeEach
    void setUp() {
        pointService = new PointServiceImpl(pointRepository, pointLogRepository);
    }

    @Nested
    @DisplayName("포인트 사용")
    class UsePoint {

        @Test
        @DisplayName("보유 포인트가 충분하면 정상적으로 차감되고 사용 로그가 남는다.")
        void useSuccess() {
            // given
            Point point = Point.create(1L);
            point.earn(10000L);

            when(pointLogRepository.findByEventId("ORDER:1:POINT_USE")).thenReturn(Optional.empty());
            when(pointRepository.findById(1L)).thenReturn(Optional.of(point));

            // when
            pointService.usePoint(1L, 3000L, "ORDER:1:POINT_USE");

            // then
            assertThat(point.getTotalPoint()).isEqualTo(7000L);
            verify(pointRepository).saveAndFlush(point);

            ArgumentCaptor<PointLog> captor = ArgumentCaptor.forClass(PointLog.class);
            verify(pointLogRepository).save(captor.capture());
            PointLog savedLog = captor.getValue();

            assertThat(savedLog.getUserId()).isEqualTo(1L);
            assertThat(savedLog.getAmount()).isEqualTo(3000L);
            assertThat(savedLog.getPointType()).isEqualTo(PointType.USE);
            assertThat(savedLog.getEventId()).isEqualTo("ORDER:1:POINT_USE");
        }

        @Test
        @DisplayName("Point row가 없는(신규) 유저가 포인트를 사용하려 하면 POINT_NOT_FOUND 예외가 발생한다.")
        void useNewUserInsufficientPoint() {
            // given
            when(pointLogRepository.findByEventId("ORDER:1:POINT_USE")).thenReturn(Optional.empty());
            when(pointRepository.findById(1L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> pointService.usePoint(1L, 300L, "ORDER:1:POINT_USE"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PointErrorCode.POINT_NOT_FOUND);

            verify(pointRepository, never()).saveAndFlush(any());
            verify(pointLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("보유 포인트보다 많은 금액을 사용하려 하면 예외가 발생한다.")
        void useInsufficientPoint() {
            // given
            Point point = Point.create(1L);
            point.earn(1000L);

            when(pointLogRepository.findByEventId("ORDER:1:POINT_USE")).thenReturn(Optional.empty());
            when(pointRepository.findById(1L)).thenReturn(Optional.of(point));

            // when & then
            assertThatThrownBy(() -> pointService.usePoint(1L, 2000L, "ORDER:1:POINT_USE"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PointErrorCode.INSUFFICIENT_POINT);
        }

        @Test
        @DisplayName("amount가 0 이하면 예외가 발생한다.")
        void useZeroAmount() {
            // given
            Point point = Point.create(1L);
            point.earn(1000L);

            when(pointLogRepository.findByEventId("ORDER:1:POINT_USE")).thenReturn(Optional.empty());
            when(pointRepository.findById(1L)).thenReturn(Optional.of(point));

            // when & then
            assertThatThrownBy(() -> pointService.usePoint(1L, 0L, "ORDER:1:POINT_USE"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PointErrorCode.ZERO_POINT_AMOUNT);
        }

        @Test
        @DisplayName("동시성 충돌이 발생하면 POINT_CONCURRENT_MODIFICATION으로 변환된다.")
        void useConcurrentModification() {
            // given
            Point point = Point.create(1L);
            point.earn(1000L);

            when(pointLogRepository.findByEventId("ORDER:1:POINT_USE")).thenReturn(Optional.empty());
            when(pointRepository.findById(1L)).thenReturn(Optional.of(point));
            when(pointRepository.saveAndFlush(point))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Point.class, 1L));

            // when & then
            assertThatThrownBy(() -> pointService.usePoint(1L, 300L, "ORDER:1:POINT_USE"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PointErrorCode.POINT_CONCURRENT_MODIFICATION);

            verify((pointLogRepository), never()).save(any());
        }

        @Test
        @DisplayName("이미 처리된 eventId면 아무 것도 하지 않고 리턴한다.")
        void useAlreadyProcessed() {
            // given
            PointLog existingLog = PointLog.use(1L, 300L, "ORDER:1:POINT_USE");
            when(pointLogRepository.findByEventId("ORDER:1:POINT_USE")).thenReturn(Optional.of(existingLog));

            // when
            pointService.usePoint(1L, 300L, "ORDER:1:POINT_USE");

            // then
            verifyNoInteractions(pointRepository);
            verify(pointLogRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("사용 금액 조회")
    class FindUsedAmount {

        @Test
        @DisplayName("eventId에 해당하는 로그가 있으면 그 금액을 반환한다.")
        void findUsedAmountExists() {
            // given
            PointLog pointLog = PointLog.use(1L, 300L, "ORDER:1:POINT_USE");
            when(pointLogRepository.findByEventId("ORDER:1:POINT_USE")).thenReturn(Optional.of(pointLog));

            // when
            Long result = pointService.findUsedAmount("ORDER:1:POINT_USE");

            // then
            assertThat(result).isEqualTo(300L);

        }

        @Test
        @DisplayName("eventId에 해당하는 로그가 없으면 0을 반환한다.")
        void findUsedAmountNotExists() {
            // given
            when(pointLogRepository.findByEventId("ORDER:1:POINT_USE")).thenReturn(Optional.empty());

            // when
            Long result = pointService.findUsedAmount("ORDER:1:POINT_USE");

            // then
            assertThat(result).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("포인트 환급")
    class RollbackPoint {

        @Test
        @DisplayName("amount가 0이하면 아무 repository도 건드리지 않고 리턴한다.")
        void rollbackZeroAmount() {
            // when
            pointService.rollbackPoint("originEventId", 0L, "rollbackEventId", true);

            // then
            verifyNoInteractions(pointRepository, pointLogRepository);
        }

        @Test
        @DisplayName("rollbackEventId가 이미 존재하면 중복 처리하지 않는다.")
        void rollbackAlreadyProcessed() {
            // given
            PointLog existingRollbackLog = PointLog.use(1L, 300L, "rollbackEventId");
            when(pointLogRepository.findByEventId("rollbackEventId")).thenReturn(Optional.of(existingRollbackLog));

            // when
            pointService.rollbackPoint("originEventId", 300L, "rollbackEventId", true);

            // then
            verify(pointLogRepository, never()).findByEventId("originEventId");
            verifyNoInteractions(pointRepository);
        }

        @Test
        @DisplayName("원본 로그는 있는데 해당 유저의 Point가 없으면 POINT_NOT_FOUND 예외가 발생한다.")
        void rollbackPointNotFound() {
            // given
            PointLog originLog = PointLog.use(1L, 300L, "originEventId");

            when(pointLogRepository.findByEventId("rollbackEventId")).thenReturn(Optional.empty());
            when(pointLogRepository.findByEventId("originEventId")).thenReturn(Optional.of(originLog));
            when(pointRepository.findById(1L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> pointService.rollbackPoint("originEventId", 300L, "rollbackEventId", true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PointErrorCode.POINT_NOT_FOUND);
        }

        @Test
        @DisplayName("정상 환급이면 잔액이 복구되고 롤백 로그가 남는다.")
        void rollbackSuccessFull() {
            // given
            Point point = Point.create(1L);
            point.earn(1000L);
            point.use(300L);

            PointLog originLog = PointLog.use(1L, 300L, "originEventId");
            ReflectionTestUtils.setField(originLog, "id", 10L);

            when(pointLogRepository.findByEventId("rollbackEventId")).thenReturn(Optional.empty());
            when(pointLogRepository.findByEventId("originEventId")).thenReturn(Optional.of(originLog));
            when(pointRepository.findById(1L)).thenReturn(Optional.of(point));

            // when
            pointService.rollbackPoint("originEventId", 300L, "rollbackEventId", true);

            // then
            assertThat(point.getTotalPoint()).isEqualTo(1000L);
            verify(pointRepository).saveAndFlush(point);

            ArgumentCaptor<PointLog> captor = ArgumentCaptor.forClass(PointLog.class);
            verify(pointLogRepository).save(captor.capture());
            PointLog savedLog = captor.getValue();
            assertThat(savedLog.getUserId()).isEqualTo(1L);
            assertThat(savedLog.getAmount()).isEqualTo(300L);
            assertThat(savedLog.getPointType()).isEqualTo(PointType.CANCELLED);
            assertThat(savedLog.getRefLogId()).isEqualTo(10L);
            assertThat(savedLog.getEventId()).isEqualTo("rollbackEventId");

        }

        @Test
        @DisplayName("정상 환급이면 잔액이 복구되고 롤백 로그가 남는다. (부분 환급)")
        void rollbackSuccessPartial() {
            // given
            Point point = Point.create(1L);
            point.earn(1000L);
            point.use(300L);

            PointLog originLog = PointLog.use(1L, 300L, "originEventId");
            ReflectionTestUtils.setField(originLog, "id", 10L);

            when(pointLogRepository.findByEventId("rollbackEventId")).thenReturn(Optional.empty());
            when(pointLogRepository.findByEventId("originEventId")).thenReturn(Optional.of(originLog));
            when(pointRepository.findById(1L)).thenReturn(Optional.of(point));

            // when
            pointService.rollbackPoint("originEventId", 150L, "rollbackEventId", false);

            // then
            assertThat(point.getTotalPoint()).isEqualTo(850L);

            ArgumentCaptor<PointLog> captor = ArgumentCaptor.forClass(PointLog.class);
            verify(pointLogRepository).save(captor.capture());
            assertThat(captor.getValue().getAmount()).isEqualTo(150L);
            assertThat(captor.getValue().getPointType()).isEqualTo(PointType.PARTIAL_CANCELLED);
        }

        @Test
        @DisplayName("동시성 충돌이 발생하면 POINT_CONCURRENT_MODIFICATION으로 변환된다.")
        void rollbackConcurrentModification() {
            // given
            Point point = Point.create(1L);
            point.earn(1000L);
            point.use(300L);

            PointLog originLog = PointLog.use(1L, 300L, "originEventId");
            ReflectionTestUtils.setField(originLog, "id", 10L);

            when(pointLogRepository.findByEventId("rollbackEventId")).thenReturn(Optional.empty());
            when(pointLogRepository.findByEventId("originEventId")).thenReturn(Optional.of(originLog));
            when(pointRepository.findById(1L)).thenReturn(Optional.of(point));
            when(pointRepository.saveAndFlush(point))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Point.class, 1L));

            // when & then
            assertThatThrownBy(() -> pointService.rollbackPoint("originEventId", 300L, "rollbackEventId", true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PointErrorCode.POINT_CONCURRENT_MODIFICATION);

            verify(pointLogRepository, never()).save(any());


        }

        @Test
        @DisplayName("원본 사용 로그가 없으면 ORIGIN_POINT_LOG_NOT_FOUND 예외가 발생한다.")
        void rollbackOriginLogNotFound() {
            // given
            when(pointLogRepository.findByEventId("rollbackEventId")).thenReturn(Optional.empty());
            when(pointLogRepository.findByEventId("originEventId")).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> pointService.rollbackPoint("originEventId", 300L, "rollbackEventId", true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(PointErrorCode.ORIGIN_POINT_LOG_NOT_FOUND);

            verifyNoInteractions(pointRepository);
        }
    }
}