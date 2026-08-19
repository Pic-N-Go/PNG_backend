package com.project.picngo.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.project.picngo.common.util.ValidationRules.NICKNAME_MESSAGE;
import static com.project.picngo.common.util.ValidationRules.NICKNAME_REGEX;

public record UserProfileUpdateRequest(
        // 가입과 같은 규칙 — 프로필 편집으로 우회해 특수문자 닉네임이 들어오지 않게 한다.
        @NotBlank
        @Pattern(regexp = NICKNAME_REGEX, message = NICKNAME_MESSAGE)
        String nickname,
        String profileImageUrl,
        // 클라이언트 입력창도 100자로 제한한다(ProfileEditScreen BIO_MAX)
        @Size(max = 100, message = "자기소개는 100자 이내여야 합니다.")
        String bio
) {
}