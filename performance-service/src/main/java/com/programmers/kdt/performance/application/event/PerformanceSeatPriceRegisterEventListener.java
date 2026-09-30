package com.programmers.kdt.performance.application.event;

import com.programmers.kdt.performance.domain.event.PerformanceSeatPriceRegisterEvent;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.exception.CommonErrorCode;
import com.programmers.kdt.performance.domain.entity.PerformanceSeatPrice;
import com.programmers.kdt.performance.infrastructure.repository.PerformanceSeatPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PerformanceSeatPriceRegisterEventListener {

    private final PerformanceSeatPriceRepository seatPriceRepository;

    @EventListener
    public void handleSeatPriceRegister(PerformanceSeatPriceRegisterEvent event) {
        if (event.seatPriceRequests().isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "좌석금액 정보");
        }

        List<PerformanceSeatPrice> seatPrices = event.seatPriceRequests().stream()
                .map(seatPrice -> seatPrice.toSeatPrice(event.performance()))
                .toList();
        seatPriceRepository.saveAll(seatPrices);
    }
}