package com.programmers.kdt.payment.exception;

import com.programmers.kdt.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PointErrorCode implements ErrorCode {

    MISSING_USER_ID(HttpStatus.BAD_REQUEST, "POI400_001", "사용자 정보가 없습니다."),
    ZERO_POINT_AMOUNT(HttpStatus.BAD_REQUEST, "POI400_002", "포인트 금액은 0원보다 커야 합니다. 금액: {0}"),
    MISSING_EVENT_ID(HttpStatus.BAD_REQUEST, "POI400_003", "eventId가 없습니다."),
    INVALID_ROLLBACK_TARGET(HttpStatus.BAD_REQUEST, "POI400_004", "환급은 사용(USE) 로그에 대해서만 가능합니다."),
    ROLLBACK_AMOUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "POI400_005", "취소 금액이 원본 사용 금액을 초과했습니다. 원본: {0}, 취소: {1}"),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "POI400_006", "from은 to보다 이후일 수 없습니다. from: {0}, to: {1}"),

    ORIGIN_POINT_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "POI404_001", "환급 대상 포인트 사용 내역을 찾을 수 없습니다. eventId: {0}"),
    POINT_NOT_FOUND(HttpStatus.NOT_FOUND, "POI404_002", "포인트 정보를 찾을 수 없습니다. userId: {0}"),

    INSUFFICIENT_POINT(HttpStatus.CONFLICT, "POI409_001", "보유 포인트가 부족합니다. 보유: {0}, 요청: {1}"),
    POINT_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "POI409_002", "포인트 처리 중 다른 요청과 충돌했습니다. 잠시 후 다시 시도해주세요."),
    ;
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    PointErrorCode(HttpStatus httpStatus, String code, String message) {
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