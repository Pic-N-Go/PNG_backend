package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum NotificationErrorCode implements BaseErrorCode {
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 알림을 찾을 수 없습니다."),
    UNAUTHORIZED_NOTIFICATION_ACCESS(HttpStatus.FORBIDDEN, "해당 알림에 대한 접근 권한이 없습니다."),
    FCM_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "FCM Token이 존재하지 않아 알림을 보낼 수 없습니다."),
    FCM_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FCM 알림 발송에 실패했습니다."),
    ASYNC_NOTIFICATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "비동기 알림 메시지 큐 소비 및 발송에 최종 실패했습니다.");

    private final HttpStatus status;
    private final String message;

    NotificationErrorCode(HttpStatus status, String message) {
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
