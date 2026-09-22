package com.programmers.kdt.payment.client.pg.toss;

import java.util.List;

public record TossCancelResponse(
        String paymentKey,
        String status,
        List<CancelDetail> cancels
) {

    public record CancelDetail(
            Long cancelAmount,
            String canceledAt,
            String cancelReason
    ) {

    }
}
