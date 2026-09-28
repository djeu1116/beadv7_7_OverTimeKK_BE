package com.programmers.kdt.common.reconciliation;

// 자동 재시도가 소진되거나 애초에 재시도 수단이 없어서 사람이 확인해야 하는 상태 불일치 유형.
// 로그 grep 대신 이 테이블 하나로 미해결 건을 조회한다.
public enum ReconciliationTaskType {
    ORDER_OUTBOX_DELIVERY_FAILED,   // 주문 outbox 이벤트가 재시도를 소진해 상대에게 끝내 전달되지 않음
    OUTBOX_DELIVERY_FAILED,         // 결제 outbox 이벤트가 재시도를 소진해 상대에게 끝내 전달되지 않음
    POINT_EARN_BATCH_FAILED,        // 공연 종료 포인트 적립 배치 자체가 실패
    POINT_EARN_FAILED,              // 포인트 적립 대상 1건이 동시성 충돌로 재시도 소진
    PG_CONFIRM_TIMEOUT_FORCE_FAILED,// PG 승인 결과 불확실(AMBIGUOUS)이 재조회 시간 초과로 강제 실패 처리됨
    POINT_ROLLBACK_FAILED,          // 결제 실패 후 포인트 롤백이 재시도를 소진
    PAYMENT_START_COMPENSATION_FAILED, // 결제 시작 보상(주문 전이 되돌리기) 실패 - 주문이 PAYMENT_STARTED에 남음
    COMPENSATION_FAILED,            // 시스템 보상 중 PG 취소가 실패 - 결제가 PAID로 남음
    REFUND_POINT_ROLLBACK_FAILED,   // 환불은 완료됐지만 포인트 환급이 실패
    PG_CONFIRM_TIMEOUT_CANCEL_UNCERTAIN, // 재조회 포기(강제 실패) 시 안전망으로 시도한 PG 취소마저 결과가 불확실 - 돈이 PG쪽에 묶여 있을 수 있음
    DAILY_SETTLEMENT_MISMATCH,      // 일일 대사 배치가 발견한 DB-PG 상태 불일치(이미 종결된 결제 대상)
    PG_RESPONSE_MISMATCH            // PG가 성공(DONE)이라 응답했는데 그 안의 orderId/금액이 기대값과 다름 - 데이터 오염/위변조 의심
}
