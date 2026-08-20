package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements BaseErrorCode {

    INVALID_LOGIN(HttpStatus.BAD_REQUEST, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    SOCIAL_ACCOUNT_HAS_NO_PASSWORD(HttpStatus.BAD_REQUEST, "소셜 계정은 비밀번호를 사용하지 않습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 가입된 이메일입니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 필요합니다."),
    EMAIL_VERIFICATION_CODE_NOT_FOUND(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었거나 존재하지 않습니다."),
    EMAIL_VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."),
    KAKAO_PROFILE_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "카카오 사용자 정보를 조회하지 못했습니다."),
    SOCIAL_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 다른 로그인 방식으로 가입된 이메일입니다."),
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요한 기능입니다."),
    FORBIDDEN_ACCESS(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 리프레시 토큰입니다."),
    ACCESS_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, "액세스 토큰이 필요합니다."),
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다."),
    ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 액세스 토큰입니다."),
    // 로그인 실패를 INVALID_LOGIN으로 뭉개면 복구 안내를 띄울 수 없다. 자격증명은 맞았다는 뜻이므로 코드를 분리한다.
    ACCOUNT_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴 처리된 계정입니다. 30일 이내에는 복구할 수 있어요."),
    RESTORE_PERIOD_EXPIRED(HttpStatus.GONE, "복구 가능 기간(30일)이 지나 되돌릴 수 없습니다."),
    // 유예 기간에는 이메일이 선점 상태다. "이미 가입된 이메일"로만 알리면 본인인데 원인을 알 수 없다.
    EMAIL_RESERVED_BY_WITHDRAWN_ACCOUNT(HttpStatus.CONFLICT, "탈퇴 대기 중인 계정의 이메일입니다. 복구해서 이어 쓸 수 있어요.");

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }


    @Override
    public HttpStatus getStatus(){
        return status;
    }

    @Override
    public String getMessage(){
        return message;
    }
}
