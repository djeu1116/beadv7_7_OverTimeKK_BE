package com.programmers.kdt.payment.client.pg;

import java.util.UUID;

public final class PgOrderIdFormatter {
    public static String format(Long orderId) {
        return "ORDER-" + orderId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
