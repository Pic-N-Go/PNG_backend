package com.project.picngo.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
        /*
         * 형식 검증은 여기가 아니라 UserService.updateMyProfile에서 한다 — @Pattern을 걸면
         * 새 규칙(2~10자) 이전에 만들어진 닉네임을 가진 계정이 자기소개만 고치려 해도 400이 난다.
         * PUT은 전체 교체라 클라이언트가 현재 닉네임을 그대로 되돌려 보내기 때문이다.
         * 서비스에서 "닉네임이 실제로 바뀐 경우"에만 검사하면 백필 없이 점진적으로 정리된다.
         */
        @NotBlank
        String nickname,
        String profileImageUrl,
        // 클라이언트 입력창도 100자로 제한한다(ProfileEditScreen BIO_MAX)
        @Size(max = 100, message = "자기소개는 100자 이내여야 합니다.")
        String bio
) {
}