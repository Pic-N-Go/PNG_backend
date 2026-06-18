package com.project.picngo.common.exception;

public record ErrorResponse(
        int status,
        String code,
        String message
) {
    public static ErrorResponse of(BaseErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getStatus().value(),
                errorCode.name(),
                errorCode.getMessage()
        );
    }
    
    public static ErrorResponse of(BaseErrorCode errorCode, String customMessage) {
        return new ErrorResponse(
                errorCode.getStatus().value(),
                errorCode.name(),
                customMessage
        );
    }
}
