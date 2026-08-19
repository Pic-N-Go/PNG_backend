package com.project.picngo.common.exception;

// Boot 4의 기본 JSON 매퍼는 Jackson 3(tools.jackson)이다. jackson 2 클래스로 잡으면 분기가 죽는다.
import tools.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.project.picngo.common.exception.code.CommonErrorCode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        BaseErrorCode errorCode = e.getErrorCode();

        // CustomException에는 스택트레이스를 남기지 않는다.
        // 이 예외는 우리가 원인을 알고 직접 던지는 것이라, 스택이 알려주는 "어디서 던졌나"를
        // 이미 에러코드와 메시지가 말해준다. 실제로 스택 96줄 중 우리 코드는 1~2줄이고
        // 나머지는 매 요청 동일한 Spring Security 필터 체인이다.
        // 외부 API 장애처럼 초당 수백 건이 쏟아지면 이 한 줄이 분당 수십 MB를 만든다
        // (부하테스트 실측: 예외 1건당 96줄·13KB, 50초에 약 59MB).
        //
        // 심각도는 상태코드로 구분한다. 5xx는 서버 문제이므로 ERROR,
        // 4xx는 정상적인 클라이언트 오류라 WARN이다.
        // 예상 못 한 예외(NPE 등)의 스택은 아래 handleException이 그대로 남긴다.
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("CustomException: {}", e.getMessage());
        } else {
            log.warn("CustomException: {}", e.getMessage());
        }

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("MethodArgumentNotValidException: {}", e.getMessage());
        
        String errorMessage = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(CommonErrorCode.INVALID_INPUT_VALUE.getMessage());
                
        return ResponseEntity
                .status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, errorMessage));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadableException: {}", e.getMessage());
        String detail = (e.getCause() instanceof InvalidFormatException ife)
                ? "허용되지 않는 값: " + ife.getValue()
                : CommonErrorCode.INVALID_INPUT_VALUE.getMessage();
        return ResponseEntity
                .status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, detail));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("MethodArgumentTypeMismatchException: {}", e.getMessage());
        String detail = "잘못된 파라미터 형식: " + e.getName();
        return ResponseEntity
                .status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, detail));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameter(MissingServletRequestParameterException e) {
        log.warn("MissingServletRequestParameterException: {}", e.getMessage());
        String detail = "필수 파라미터 누락: " + e.getParameterName();
        return ResponseEntity
                .status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, detail));
    }

    // multipart 파트 이름이 틀리거나 빠지면 아래 Exception 핸들러에 걸려 500이 된다. 클라이언트 오류라 400으로 내린다.
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestPart(MissingServletRequestPartException e) {
        log.warn("MissingServletRequestPartException: {}", e.getMessage());
        String detail = "필수 파트 누락: " + e.getRequestPartName();
        return ResponseEntity
                .status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, detail));
    }

    // 업로드 용량 초과도 클라이언트 오류다. 전역 Exception 핸들러에 걸리면 500이 된다.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("MaxUploadSizeExceededException: {}", e.getMessage());
        return ResponseEntity
                .status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, "업로드 용량이 허용 범위를 초과했습니다."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
        log.warn("HttpMediaTypeNotSupportedException: {}", e.getMessage());
        return ResponseEntity
                .status(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    /** MySQL duplicate entry. 이 값만 사용자가 고쳐서 해결할 수 있는 오류다. */
    private static final int MYSQL_DUPLICATE_ENTRY = 1062;

    /**
     * 유니크 제약 위반. 닉네임처럼 "검사 후 저장" 구조인 값은 검사와 INSERT 사이에 다른 요청이
     * 같은 값을 넣으면 여기로 온다(동시 가입 경합). 전역 Exception 핸들러에 걸리면 500이 되는데,
     * 사용자 입장에서는 다른 값을 고르면 되는 400이다.
     *
     * 중복 키만 400으로 내린다. FK·NOT NULL 위반은 사용자가 고칠 수 없는 서버 버그인데,
     * 같이 400으로 삼키면 스택트레이스까지 사라져 조사할 단서가 없어진다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        boolean duplicateEntry = cause instanceof SQLException sqlException
                && sqlException.getErrorCode() == MYSQL_DUPLICATE_ENTRY;
        if (!duplicateEntry) {
            return handleException(e);
        }

        log.warn("DataIntegrityViolationException(duplicate): {}", cause.getMessage());
        return ResponseEntity
                .status(CommonErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT_VALUE, "이미 사용 중인 값입니다. 다시 시도해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Exception: {}", e.getMessage(), e);
        return ResponseEntity
                .status(CommonErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }
}
