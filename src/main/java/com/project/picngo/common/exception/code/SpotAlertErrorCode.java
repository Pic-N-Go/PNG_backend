package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum SpotAlertErrorCode implements BaseErrorCode {
    SPOT_ALERT_NOT_FOUND_OR_UNAUTHORIZED(HttpStatus.NOT_FOUND, "출사알림을 찾을 수 없거나 권한이 없습니다."),
    SPOT_ALERT_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "출사알림 아이템을 찾을 수 없습니다."),
    INVALID_ALERT_TIMING(HttpStatus.BAD_REQUEST, "알림 시점(alertTimingDays)은 0(당일), 1(1일 전), 3(3일 전)만 허용됩니다.");

    private final HttpStatus status;
    private final String message;

    SpotAlertErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
