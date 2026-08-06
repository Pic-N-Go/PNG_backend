package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum AlbumErrorCode implements BaseErrorCode {

    ALBUM_NOT_FOUND(HttpStatus.NOT_FOUND, "앨범을 찾을 수 없습니다."),
    ALBUM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "앨범에 접근할 권한이 없습니다."),
    ALBUM_PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "앨범 사진을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    AlbumErrorCode(HttpStatus status, String message) {
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
