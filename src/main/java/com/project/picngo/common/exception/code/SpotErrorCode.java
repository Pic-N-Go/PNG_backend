package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum SpotErrorCode implements BaseErrorCode {
    SEARCH_KEYWORD_REQUIRED(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요."),
    MAP_BOUNDS_REQUIRED(HttpStatus.BAD_REQUEST, "지도 영역 좌표를 모두 입력해주세요."),
    INVALID_MAP_BOUNDS(HttpStatus.BAD_REQUEST, "지도 영역 좌표가 올바르지 않습니다."),
    SPOT_NOT_FOUND(HttpStatus.NOT_FOUND, "스팟을 찾을 수 없습니다."),
    SYNC_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "이미 다른 TourAPI 동기화 작업이 진행 중입니다.");

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
