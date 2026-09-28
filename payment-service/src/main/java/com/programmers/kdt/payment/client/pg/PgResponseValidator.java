package com.programmers.kdt.payment.client.pg;

import java.util.Objects;

// PG 재조회 응답의 orderId/금액을 우리가 기대한 값과 비교하는 순수 로직만 분리 - HTTP(TossPgclient)와
// 분리해서 실제 Toss 연동 없이도 이 검증 자체는 단위 테스트로 실측 확인할 수 있게 한다.
public final class PgResponseValidator {

    private PgResponseValidator() {
    }

    public static void validate(String paymentKey, String actualOrderId, Long actualAmount,
                                 String expectedOrderId, Long expectedAmount) {
        if (!Objects.equals(actualOrderId, expectedOrderId) || !Objects.equals(actualAmount, expectedAmount)) {
            throw new PgResponseMismatchException(paymentKey, expectedOrderId, expectedAmount, actualOrderId, actualAmount);
        }
    }
}
