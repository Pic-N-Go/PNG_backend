package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum InquiryErrorCode implements BaseErrorCode {

    INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "1:1 문의를 찾을 수 없습니다."),
    UNAUTHORIZED_INQUIRY_ACCESS(HttpStatus.FORBIDDEN, "해당 문의에 대한 접근 권한이 없습니다.");

    private final HttpStatus status;
    private final String message;

    InquiryErrorCode(HttpStatus status, String message) {
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
