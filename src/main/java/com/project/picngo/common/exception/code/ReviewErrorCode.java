package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum ReviewErrorCode implements BaseErrorCode {
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."),
    REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "본인의 리뷰만 조회/수정/삭제할 수 있습니다."),
    REVIEW_INVALID_SORT(HttpStatus.BAD_REQUEST, "sort 값은 LATEST, RATING_HIGH, RATING_LOW 중 하나여야 합니다."),
    // 공용 ImageErrorCode.IMAGE_FILE_TOO_MANY는 메시지가 10장으로 고정돼 있어 리뷰 전용 코드를 둔다.
    REVIEW_PHOTO_TOO_MANY(HttpStatus.BAD_REQUEST, "리뷰 사진은 최대 5장까지 등록할 수 있습니다."),
    REVIEW_EQUIPMENT_INFO_TOO_LONG(HttpStatus.BAD_REQUEST, "장비 정보는 모두 합쳐 100자를 넘을 수 없습니다."),
    REVIEW_PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 리뷰의 사진을 찾을 수 없습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 이 스팟에 리뷰를 작성했습니다. 기존 리뷰를 수정해 주세요.");

    private final HttpStatus status;
    private final String message;

    ReviewErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() { return status; }

    @Override
    public String getMessage() { return message; }
}
