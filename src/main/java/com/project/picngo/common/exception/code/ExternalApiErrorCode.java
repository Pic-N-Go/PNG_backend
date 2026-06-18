package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum ExternalApiErrorCode implements BaseErrorCode {
    KAKAO_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "카카오 길찾기 API 응답 이상"),
    WEATHER_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "날씨/일출일몰 API 호출 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ExternalApiErrorCode(HttpStatus status, String message) {
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
