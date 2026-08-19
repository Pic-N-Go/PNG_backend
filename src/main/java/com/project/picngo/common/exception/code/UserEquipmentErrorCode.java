package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum UserEquipmentErrorCode implements BaseErrorCode {

    USER_EQUIPMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자 장비를 찾을 수 없습니다."),
    USER_EQUIPMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 장비입니다."),
    USER_EQUIPMENT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "장비는 최대 20개까지 등록할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    UserEquipmentErrorCode(HttpStatus status, String message) {
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
