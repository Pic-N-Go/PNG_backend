package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum SpotErrorCode implements BaseErrorCode {
    SEARCH_KEYWORD_REQUIRED(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요."),
    INVALID_SPOT_CATEGORY(HttpStatus.BAD_REQUEST, "지원하지 않는 스팟 카테고리입니다."),
    MAP_BOUNDS_REQUIRED(HttpStatus.BAD_REQUEST, "지도 영역 좌표를 모두 입력해주세요."),
    INVALID_MAP_BOUNDS(HttpStatus.BAD_REQUEST, "지도 영역 좌표가 올바르지 않습니다."),
    SPOT_NOT_FOUND(HttpStatus.NOT_FOUND, "스팟을 찾을 수 없습니다."),
    CHECKLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "체크리스트 항목을 찾을 수 없습니다."),
    CHECKLIST_ITEM_FORBIDDEN(HttpStatus.FORBIDDEN, "본인의 체크리스트 항목만 삭제할 수 있습니다."),
    CHECKLIST_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "체크리스트 항목은 최대 10개까지 추가할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    SpotErrorCode(HttpStatus status, String message) {
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
