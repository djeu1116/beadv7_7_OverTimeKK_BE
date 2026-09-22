package com.programmers.kdt.payment.client.refund;

import java.time.LocalDate;

public interface PerformanceClient {
    LocalDate getPerformanceDate(Long ticketId);
}
