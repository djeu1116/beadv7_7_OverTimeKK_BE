package com.programmers.kdt.performance.application.event;

import com.programmers.kdt.performance.domain.event.PerformanceSeatPriceRegisterEvent;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.performance.presentation.dto.PerformanceSeatPriceRequest;
import com.programmers.kdt.performance.domain.entity.Performance;
import com.programmers.kdt.performance.infrastructure.repository.PerformanceSeatPriceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PerformanceSeatPriceRegisterEventListenerTest {

    @Mock private PerformanceSeatPriceRepository seatPriceRepository;
    @InjectMocks private PerformanceSeatPriceRegisterEventListener listener;

    @Test
    @DisplayName("좌석금액 등록 이벤트를 받으면 좌석금액을 저장한다.")
    void handleSeatPriceRegister() {
        Performance performance = new Performance();
        ReflectionTestUtils.setField(performance, "performanceId", 1L);

        PerformanceSeatPriceRequest seatPriceRequest = new PerformanceSeatPriceRequest("VIP", 150000L);

        listener.handleSeatPriceRegister(new PerformanceSeatPriceRegisterEvent(performance, List.of(seatPriceRequest)));

        verify(seatPriceRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("좌석금액 정보가 비어 있으면 예외가 발생한다.")
    void handleSeatPriceRegister_empty() {
        Performance performance = new Performance();
        ReflectionTestUtils.setField(performance, "performanceId", 1L);

        assertThatThrownBy(() -> listener.handleSeatPriceRegister(new PerformanceSeatPriceRegisterEvent(performance, List.of())))
                .isInstanceOf(BusinessException.class);
    }
}