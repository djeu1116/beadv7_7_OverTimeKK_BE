package com.programmers.kdt.performance.application.event;

import com.programmers.kdt.performance.domain.event.PerformanceSessionRegisterEvent;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.performance.presentation.dto.RegisterPerformanceSessionRequest;
import com.programmers.kdt.performance.domain.entity.Performance;
import com.programmers.kdt.performance.infrastructure.repository.PerformanceSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PerformanceSessionRegisterEventListenerTest {

    @Mock private PerformanceSessionRepository sessionRepository;
    @InjectMocks private PerformanceSessionRegisterEventListener listener;

    @Test
    @DisplayName("회차 등록 이벤트를 받으면 회차를 저장한다.")
    void handleSessionRegister() {
        Performance performance = new Performance();
        ReflectionTestUtils.setField(performance, "performanceId", 1L);

        RegisterPerformanceSessionRequest sessionRequest = new RegisterPerformanceSessionRequest(
                1L, "도라에몽", LocalDateTime.of(2026, 8, 15, 14, 0, 0)
        );

        listener.handleSessionRegister(new PerformanceSessionRegisterEvent(performance, List.of(sessionRequest)));

        verify(sessionRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("회차 정보가 비어 있으면 예외가 발생한다.")
    void handleSessionRegister_empty() {
        Performance performance = new Performance();
        ReflectionTestUtils.setField(performance, "performanceId", 1L);

        assertThatThrownBy(() -> listener.handleSessionRegister(new PerformanceSessionRegisterEvent(performance, List.of())))
                .isInstanceOf(BusinessException.class);
    }
}
