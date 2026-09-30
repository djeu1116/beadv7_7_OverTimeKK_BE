package com.programmers.kdt.payment.application.service.util;

public final class PointEventIds {

    // attemptSeq로 스코프해서 결제 재시도마다 다른 eventId가 나오게 함
    public static String useEventId(Long orderId, int attemptSeq) {
        return "ORDER:" + orderId + ":ATTEMPT:" + attemptSeq + ":POINT_USE";
    }

    public static String rollbackEventId(Long orderId, int attemptSeq) {
        return "ORDER:" + orderId + ":ATTEMPT:" + attemptSeq + ":POINT_ROLLBACK_REFUND";
    }

    public static String rollbackFailEventId(Long orderId, int attemptSeq) {
        return "ORDER:" + orderId + ":ATTEMPT:" + attemptSeq + ":POINT_ROLLBACK_FAIL";
    }
}
