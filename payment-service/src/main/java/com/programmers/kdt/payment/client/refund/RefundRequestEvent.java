package com.programmers.kdt.payment.client.refund;

import java.time.LocalDateTime;

public record RefundRequestEvent(
        Long paymentId,
        String reason,
        LocalDateTime requestAt
) {
}
