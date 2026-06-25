package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum SpotErrorCode implements BaseErrorCode {
    SPOT_NOT_FOUND(HttpStatus.NOT_FOUND, "스팟을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    SpotErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() { return status; }

    @Override
    public String getMessage() { return message; }
}
