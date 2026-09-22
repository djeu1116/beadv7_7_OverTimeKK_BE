package com.programmers.kdt.payment.exception;

import com.programmers.kdt.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_KEY_MISMATCH(HttpStatus.BAD_REQUEST, "PAY400_001", "요청한 결제 정보가 일치하지 않습니다."),
    INVALID_PAYMENT_AMOUNT(HttpStatus.BAD_REQUEST, "PAY400_002", "결제 금액이 유효하지 않습니다."),
    ZERO_PAYMENT_AMOUNT(HttpStatus.BAD_REQUEST, "PAY400_003", "결제 금액은 0보다 커야합니다. 금액: {0}"),
    INVALID_REFUND_AMOUNT(HttpStatus.BAD_REQUEST, "PAY400_004", "환불 금액이 유효하지 않습니다. 환불금액: {0}, 취소요청금액: {1}"),
    MISSING_ORDER_ID(HttpStatus.BAD_REQUEST, "PAY400_005", "주문 정보가 없습니다."),
    MISSING_PAYMENT_ID(HttpStatus.BAD_REQUEST, "PAY400_006", "결제 정보가 없습니다."),
    ZERO_REFUND_AMOUNT(HttpStatus.BAD_REQUEST, "PAY400_007", "환불 금액은 0원보다 커야 합니다. 금액: {0}"),
    MISSING_TRANSACTION_KEY(HttpStatus.BAD_REQUEST, "PAY400_008", "결제 승인 키(transactionKey)가 없습니다."),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "PAY400_009", "멱등키가 없습니다."),

    PAYMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PAY403_001", "본인 결제 건에 대해서만 접근할 수 있습니다."),

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY404_001", "결제 내역을 찾을 수 없습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY404_002", "주문 내역을 찾을 수 없습니다."),
    PERFORMANCE_DATE_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY404_003", "공연일 정보를 찾을 수 없습니다. ticketId: {0}"),

    PAYMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "PAY409_001", "이미 해당 주문에 대한 결제가 존재합니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "PAY409_002", "현재 결제 상태에서는 처리할 수 없는 요청입니다. 현재 상태: {0}"),
    PAYMENT_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "PAY409_003", "다른 요청과 동시에 처리되어 실패했습니다. 잠시 후 다시 시도해주세요."),
    REFUND_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "PAY409_004", "이미 처리 중인 환불 요청이 있습니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "PAY409_005", "동일한 멱등키로 다른 요청이 이미 처리되었습니다."),
    ORDER_ALREADY_EXPIRED(HttpStatus.CONFLICT, "PAY409_006", "만료된 주문은 결제할 수 없습니다."),
    ORDER_NOT_PENDING(HttpStatus.CONFLICT, "PAY409_007", "결제 대기 상태의 주문만 결제를 시작할 수 있습니다."),

    REFUND_PERIOD_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT, "PAY422_001", "환불 가능 기간이 지나 취소할 수 없습니다."),

    ENCRYPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PAY500_001", "결제 정보 암호화 중 오류가 발생했습니다."),
    DECRYPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PAY500_002", "결제 정보 복호화 중 오류가 발생했습니다."),

    PG_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "PAY502_001", "PG사 요청 처리 중 오류가 발생했습니다."),
    ORDER_SERVICE_FAILED(HttpStatus.BAD_GATEWAY, "PAY502_002", "주문 처리 중 오류가 발생했습니다."),
    INVALID_PAYMENT_KEY(HttpStatus.BAD_GATEWAY, "PAY502_002", "PG사로부터 유효한 결제 키를 받지 못했습니다."),
    PERFORMANCE_SERVICE_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "PAY502_003", "공연 서비스 요청 처리 중 오류가 발생했습니다."),
    REFUND_SERVICE_FAILED(HttpStatus.BAD_REQUEST, "PAY502_004", "환불 처리 중 예외가 발생했습니다.")
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    PaymentErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}