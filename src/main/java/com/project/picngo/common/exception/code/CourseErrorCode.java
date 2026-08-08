package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum CourseErrorCode implements BaseErrorCode {
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다."),
    COURSE_CHECKLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "코스 체크리스트를 찾을 수 없습니다."),
    EXCEEDED_MAX_COURSE_DAYS(HttpStatus.BAD_REQUEST, "코스 일정은 최대 15일까지 설정할 수 있습니다."),
    EXCEEDED_MAX_DAY_SPOTS(HttpStatus.BAD_REQUEST, "하루(DAY)당 스팟은 최대 10개까지 등록할 수 있습니다."),
    INVALID_COURSE_DATE_RANGE(HttpStatus.BAD_REQUEST, "코스 시작일은 종료일 이전이어야 합니다.");

    private final HttpStatus status;
    private final String message;

    CourseErrorCode(HttpStatus status, String message) {
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
