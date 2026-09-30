package com.programmers.kdt.order.domain.exception;

import com.programmers.kdt.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OrderErrorCode implements ErrorCode {
    INVALID_ORDER_TYPE(HttpStatus.BAD_REQUEST, "ORD400_001","지원하지 않는 주문 구분값입니다."),
    TICKET_ID_REQUIRED(HttpStatus.BAD_REQUEST, "ORD400_002", "주문할 티켓 정보가 필요합니다."),
    USER_ID_REQUIRED(HttpStatus.BAD_REQUEST, "ORD400_003", "주문자 ID는 필수입니다."),
    INVALID_ORDER_AMOUNT(HttpStatus.BAD_REQUEST, "ORD400_004", "주문 금액은 0보다 커야 합니다."),
    ORDER_ITEMS_REQUIRED(HttpStatus.BAD_REQUEST, "ORD400_005", "주문할 상품이 없습니다."),
    TICKET_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "ORD400_006", "유효하지 않은 티켓이거나 점유 정보가 일치하지 않습니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ORDER_403", "해당 주문에 접근할 권한이 없습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORD404_001", "주문을 찾을 수 없습니다."),
    TICKET_INFO_NOT_FOUND(HttpStatus.NOT_FOUND, "ORD404_002","주문에 해당하는 티켓 정보를 찾을 수 없습니다."),
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "ORD404_003", "티켓을 찾을 수 없습니다."),
    ORDER_NOT_PENDING(HttpStatus.CONFLICT, "ORD409_001", "결제 대기 상태의 주문이 아닙니다."),
    ORDER_NOT_PAYMENT_STARTED(HttpStatus.CONFLICT, "ORD409_002", "결제가 시작된 주문만 완료할 수 있습니다."),
    ORDER_ALREADY_EXPIRED(HttpStatus.CONFLICT, "ORD409_003", "이미 만료된 주문입니다."),
    ORDER_NOT_COMPLETED(HttpStatus.CONFLICT, "ORD409_004", "완료된 주문만 취소할 수 있습니다."),
    ORDER_ALREADY_CANCELLED(HttpStatus.CONFLICT, "ORD409_005", "이미 취소된 주문입니다."),
    ORDER_CANCEL_NOT_REQUESTED(HttpStatus.CONFLICT, "ORD409_006", "취소 접수 상태의 주문이 아닙니다."),
    TICKET_VALIDATION_RESPONSE_EMPTY(HttpStatus.BAD_GATEWAY, "ORD502_001", "티켓 유효성 검증 응답이 비어 있습니다."),
    TICKET_INFO_RESPONSE_EMPTY(HttpStatus.BAD_GATEWAY,"ORD502_002", "티켓 정보 조회 응답이 비어 있습니다."),
    TICKET_INFO_GET_FAILED(HttpStatus.BAD_GATEWAY,"ORD502_003", "티켓 정보 조회에 실패했습니다."),
    TICKET_RESERVE_FAILED(HttpStatus.BAD_GATEWAY, "ORD502_004", "티켓 예약 확정에 실패했습니다."),
    TICKET_RELEASE_FAILED(HttpStatus.BAD_GATEWAY, "ORD502_005", "티켓 해제 요청에 실패했습니다.");
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    OrderErrorCode(HttpStatus httpStatus, String code, String message){
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
