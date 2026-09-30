package com.programmers.kdt.user.domain.exception;

import com.programmers.kdt.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {
    MAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "USR500_001", "이메일 발송에 실패했습니다."),

    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "USR400_001", "이메일 인증을 먼저 완료해주세요."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "USR400_002", "인증 코드가 올바르지 않습니다."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "USR409_001", "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USR409_002", "이미 사용 중인 이메일입니다."),
    DUPLICATE_BUSINESS_NUMBER(HttpStatus.CONFLICT, "USR409_003", "이미 등록된 사업자번호입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USR404_001", "존재하지 않는 아이디입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "USR401_001", "비밀번호가 일치하지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "USR401_002", "다시 로그인해주세요."),
    WITHDRAWN_USER(HttpStatus.FORBIDDEN, "USR403_001", "탈퇴한 계정입니다.");


    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    UserErrorCode(HttpStatus httpStatus, String code, String message) {
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
