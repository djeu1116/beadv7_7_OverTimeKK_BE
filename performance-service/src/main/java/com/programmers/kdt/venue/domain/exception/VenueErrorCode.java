package com.programmers.kdt.venue.domain.exception;

import com.programmers.kdt.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum VenueErrorCode implements ErrorCode {
    SEAT_INFORMATION_NOT_FOUND(HttpStatus.NOT_FOUND, "VEN404_001", "{0} 홀에 대한 좌석 정보가 없습니다."),
    HALL_INFORMATION_NOT_FOUND(HttpStatus.NOT_FOUND, "VEN404_002", "홀에 대한 정보가 없습니다.");

    public final HttpStatus httpStatus;
    public final String code;
    public final String message;

}
