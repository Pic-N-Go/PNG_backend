package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum CourseErrorCode implements BaseErrorCode {
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다."),
    COURSE_CHECKLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "코스 체크리스트를 찾을 수 없습니다."),
    EXCEEDED_MAX_COURSE_DAYS(HttpStatus.BAD_REQUEST, "코스 일정은 최대 15일까지 설정할 수 있습니다."),
    EXCEEDED_MAX_DAY_SPOTS(HttpStatus.BAD_REQUEST, "하루(DAY)당 스팟은 최대 10개까지 등록할 수 있습니다."),
    INVALID_COURSE_DATE_RANGE(HttpStatus.BAD_REQUEST, "코스 시작일은 종료일 이전이어야 합니다."),

    // 낙관적 락 충돌. 같은 코스를 거의 동시에 두 번 저장했을 때 나중 요청이 받는다
    // (저장 버튼 연타, 또는 같은 계정으로 다른 기기에서 편집).
    // 재시도로는 풀리지 않는다 - 요청이 들고 온 목록 자체가 낡은 것이므로,
    // 그대로 다시 보내면 같은 충돌이 반복되거나 남의 변경을 덮어쓴다.
    // 클라이언트는 코스를 다시 불러온 뒤 사용자가 판단하게 해야 한다.
    COURSE_MODIFIED_CONCURRENTLY(HttpStatus.CONFLICT,
            "다른 곳에서 코스가 변경되었습니다. 새로고침 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;

    CourseErrorCode(HttpStatus status, String message) {
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
