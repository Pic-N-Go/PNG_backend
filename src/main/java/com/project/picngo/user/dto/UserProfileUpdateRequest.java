package com.project.picngo.user.dto;

import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
        String nickname,
        String profileImageUrl,
        // 클라이언트 입력창도 100자로 제한한다(ProfileEditScreen BIO_MAX)
        @Size(max = 100, message = "자기소개는 100자 이내여야 합니다.")
        String bio
) {
}