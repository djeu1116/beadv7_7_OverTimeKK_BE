package com.programmers.kdt.payment.infrastructure.client.refund;

import java.time.LocalDate;

public interface PerformanceClient {
    LocalDate getPerformanceDate(Long ticketId);
}
