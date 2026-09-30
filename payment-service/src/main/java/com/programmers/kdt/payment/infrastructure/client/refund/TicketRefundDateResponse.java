package com.programmers.kdt.payment.infrastructure.client.refund;

import java.time.LocalDate;

public record TicketRefundDateResponse(
        LocalDate performanceDate
) {
}
