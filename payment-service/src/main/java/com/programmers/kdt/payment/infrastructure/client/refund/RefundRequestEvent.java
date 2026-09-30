package com.programmers.kdt.payment.infrastructure.client.refund;

import java.time.LocalDateTime;

public record RefundRequestEvent(
        Long paymentId,
        String reason,
        LocalDateTime requestAt
) {
}
