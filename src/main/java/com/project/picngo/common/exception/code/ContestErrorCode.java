package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum ContestErrorCode implements BaseErrorCode {

    CONTEST_NOT_FOUND(HttpStatus.NOT_FOUND, "콘테스트를 찾을 수 없습니다."),
    CURRENT_CONTEST_NOT_FOUND(HttpStatus.NOT_FOUND, "현재 진행 중인 콘테스트를 찾을 수 없습니다."),
    ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "출품작을 찾을 수 없습니다."),
    VOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "투표 내역을 찾을 수 없습니다."),

    NOT_SUBMITTING_PERIOD(HttpStatus.CONFLICT, "출품 기간이 아닙니다."),
    NOT_VOTING_PERIOD(HttpStatus.CONFLICT, "투표 기간이 아닙니다."),
    RESULT_NOT_OPENED(HttpStatus.CONFLICT, "아직 결과 발표 전입니다."),

    ENTRY_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "출품 가능 개수를 초과했습니다."),
    VOTE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "투표 가능 횟수를 모두 사용했습니다."),
    ALREADY_VOTED(HttpStatus.CONFLICT, "이미 투표한 출품작입니다."),
    CANNOT_VOTE_OWN_ENTRY(HttpStatus.CONFLICT, "내 출품작에는 투표할 수 없습니다."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 출품작입니다."),

    CONTEST_PERIOD_OVERLAP(HttpStatus.CONFLICT, "직전 콘테스트의 결과 발표 전에는 다음 회차를 시작할 수 없습니다."),
    CONTEST_START_IN_PAST(HttpStatus.BAD_REQUEST, "콘테스트 시작 시각은 현재보다 이전일 수 없습니다."),

    NOT_MY_ENTRY(HttpStatus.FORBIDDEN, "내 출품작만 처리할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    ContestErrorCode(HttpStatus status, String message) {
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
