package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum WishlistErrorCode implements BaseErrorCode {
    WISHLIST_NOT_FOUND_OR_UNAUTHORIZED(HttpStatus.NOT_FOUND, "위시리스트를 찾을 수 없거나 권한이 없습니다."),
    WISHLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "위시리스트 아이템을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    WishlistErrorCode(HttpStatus status, String message) {
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
