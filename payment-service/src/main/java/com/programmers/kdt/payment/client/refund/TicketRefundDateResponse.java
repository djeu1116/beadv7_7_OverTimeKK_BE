package com.programmers.kdt.payment.client.refund;

import java.time.LocalDate;

public record TicketRefundDateResponse(
        LocalDate performanceDate
) {
}
