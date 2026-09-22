package com.programmers.kdt.payment.client.refund;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Profile("!performance-api")
@Component
public class MockPerformanceClient implements PerformanceClient {
    @Override
    public LocalDate getPerformanceDate(Long ticketId) {
        return LocalDate.now().plusDays(7); // 임시: 7일 뒤 공연으로 가정
    }
}
