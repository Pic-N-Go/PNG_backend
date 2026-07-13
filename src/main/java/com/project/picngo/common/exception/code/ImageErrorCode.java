package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum ImageErrorCode implements BaseErrorCode {
    IMAGE_FILE_EMPTY(HttpStatus.BAD_REQUEST, "업로드할 이미지 파일이 비어 있습니다."),
    IMAGE_FILE_TOO_MANY(HttpStatus.BAD_REQUEST, "이미지는 최대 10장까지 업로드할 수 있습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다."),
    IMAGE_PRESIGNED_URL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 접근 URL 생성에 실패했습니다."),
    INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "올바른 이미지 파일이 아닙니다.");

    private final HttpStatus status;
    private final String message;

    ImageErrorCode(HttpStatus status, String message) {
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
