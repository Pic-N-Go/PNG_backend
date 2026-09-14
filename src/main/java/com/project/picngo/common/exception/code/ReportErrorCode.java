package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum ReportErrorCode implements BaseErrorCode {

    DUPLICATE_REPORT(HttpStatus.CONFLICT, "이미 신고한 콘텐츠입니다."),
    SELF_REPORT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인이 작성한 콘텐츠는 신고할 수 없습니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다."),
    REPORT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 신고입니다."),
    INVALID_REPORT_STATUS(HttpStatus.BAD_REQUEST, "처리 상태는 RESOLVED 또는 DISMISSED여야 합니다."),
    UNSUPPORTED_REPORT_TARGET(HttpStatus.BAD_REQUEST, "아직 삭제를 지원하지 않는 신고 대상입니다.");

    private final HttpStatus status;
    private final String message;

    ReportErrorCode(HttpStatus status, String message) {
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
