package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum BookmarkErrorCode implements BaseErrorCode {
    COLLECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "북마크 컬렉션을 찾을 수 없습니다."),
    COLLECTION_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "북마크 컬렉션은 최대 5개까지 만들 수 있습니다."),
    COLLECTION_NAME_DUPLICATE(HttpStatus.CONFLICT, "이미 같은 이름의 컬렉션이 있습니다."),
    INVALID_COLLECTION_COLOR(HttpStatus.BAD_REQUEST, "허용되지 않는 색상 키입니다."),
    INVALID_COLLECTION_ICON(HttpStatus.BAD_REQUEST, "허용되지 않는 아이콘 키입니다.");

    private final HttpStatus status;
    private final String message;

    BookmarkErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() { return status; }

    @Override
    public String getMessage() { return message; }
}
