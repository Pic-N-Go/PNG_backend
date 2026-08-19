package com.project.picngo.common.util;

/**
 * 클라이언트와 짝을 이루는 입력 형식 규칙.
 * 프론트엔드 {@code src/constants/validation.ts}와 같은 값이어야 한다 — 한쪽만 고치면
 * 클라이언트에서 통과한 값이 서버에서 400으로 떨어진다.
 */
public final class ValidationRules {

    /** 한글/영문/숫자 2~10자. 특수문자·공백 불가. */
    public static final String NICKNAME_REGEX = "^[가-힣a-zA-Z0-9]{2,10}$";
    /** 정규식과 같은 값이어야 한다 — 소셜 닉네임을 규칙에 맞게 다듬을 때 쓴다. */
    public static final int NICKNAME_MIN = 2;
    public static final int NICKNAME_MAX = 10;
    public static final String NICKNAME_MESSAGE = "닉네임은 한글/영문/숫자 2~10자여야 합니다. (특수문자 불가)";

    private ValidationRules() {
    }
}
