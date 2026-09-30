package com.programmers.kdt.ticket.application.service.impl;

import com.programmers.kdt.ticket.infrastructure.cache.TicketZoneCacheStore;
import com.programmers.kdt.ticket.presentation.dto.SessionZoneKey;
import com.programmers.kdt.ticket.domain.entity.Ticket;
import com.programmers.kdt.ticket.domain.entity.TicketStatus;
import com.programmers.kdt.ticket.domain.event.StandbyCheckRequestEvent;
import com.programmers.kdt.ticket.infrastructure.repository.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketReleaseServiceImplTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TicketZoneCacheStore ticketZoneCacheStore;

    @InjectMocks private TicketReleaseServiceImpl ticketReleaseService;

    private static Ticket holdTicket(Long ticketId, Long userId, LocalDateTime holdExpiredAt, String holdKey) {
        Ticket ticket = Ticket.create(1L, 1L, "A", "1", String.valueOf(ticketId), 10000L);
        ReflectionTestUtils.setField(ticket, "ticketId", ticketId);
        ticket.holdTicket(userId, holdExpiredAt, holdKey);
        return ticket;
    }

    @Nested
    @DisplayName("점유 만료 티켓 해제")
    class ReleaseExpiredHoldTicket {

        @Test
        @DisplayName("같은 구역에 잔여석이 있으면 AVAILABLE로 해제된다.")
        void releasedToAvailable() {
            Ticket ticket = holdTicket(1L, 100L, LocalDateTime.now().minusMinutes(1), "hold-key");
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
            when(ticketRepository.existsByPerformanceIdAndSessionNumAndTicketStatusAndZone(any(), any(), eq(TicketStatus.AVAILABLE), anyString()))
                    .thenReturn(true);

            ticketReleaseService.releaseExpiredHoldTicket(1L, new HashMap<>());

            assertThat(ticket.getTicketStatus()).isEqualTo(TicketStatus.AVAILABLE);
            assertThat(ticket.getBuyUserId()).isNull();
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("같은 구역이 매진 상태면 대기 확인 이벤트를 발행하고 상태는 HOLD로 유지한다.")
        void zoneSoldOut_publishesStandbyCheckEvent() {
            Ticket ticket = holdTicket(1L, 100L, LocalDateTime.now().minusMinutes(1), "hold-key");
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
            when(ticketRepository.existsByPerformanceIdAndSessionNumAndTicketStatusAndZone(any(), any(), eq(TicketStatus.AVAILABLE), anyString()))
                    .thenReturn(false);

            ticketReleaseService.releaseExpiredHoldTicket(1L, new HashMap<>());

            assertThat(ticket.getTicketStatus()).isEqualTo(TicketStatus.HOLD);
            verify(eventPublisher).publishEvent(any(StandbyCheckRequestEvent.class));
        }

        @Test
        @DisplayName("이미 HOLD 상태가 아니면 아무 것도 하지 않는다.")
        void notHold_doesNothing() {
            Ticket ticket = holdTicket(1L, 100L, LocalDateTime.now().minusMinutes(1), "hold-key");
            ticket.releaseToAvailable();
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

            ticketReleaseService.releaseExpiredHoldTicket(1L, new HashMap<>());

            assertThat(ticket.getTicketStatus()).isEqualTo(TicketStatus.AVAILABLE);
            verify(ticketRepository, never())
                    .existsByPerformanceIdAndSessionNumAndTicketStatusAndZone(any(), any(), any(), anyString());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("같은 회차/구역 티켓을 연속 처리하면 잔여석 조회 캐시를 재사용한다.")
        void reusesZoneAvailabilityCache() {
            Ticket ticket1 = holdTicket(1L, 100L, LocalDateTime.now().minusMinutes(1), "key1");
            Ticket ticket2 = holdTicket(2L, 200L, LocalDateTime.now().minusMinutes(2), "key2");
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket1));
            when(ticketRepository.findById(2L)).thenReturn(Optional.of(ticket2));
            when(ticketRepository.existsByPerformanceIdAndSessionNumAndTicketStatusAndZone(any(), any(), eq(TicketStatus.AVAILABLE), anyString()))
                    .thenReturn(true);

            Map<SessionZoneKey, Boolean> cache = new HashMap<>();
            ticketReleaseService.releaseExpiredHoldTicket(1L, cache);
            ticketReleaseService.releaseExpiredHoldTicket(2L, cache);

            assertThat(ticket1.getTicketStatus()).isEqualTo(TicketStatus.AVAILABLE);
            assertThat(ticket2.getTicketStatus()).isEqualTo(TicketStatus.AVAILABLE);
            verify(ticketRepository, times(1))
                    .existsByPerformanceIdAndSessionNumAndTicketStatusAndZone(any(), any(), eq(TicketStatus.AVAILABLE), anyString());
        }
    }
}