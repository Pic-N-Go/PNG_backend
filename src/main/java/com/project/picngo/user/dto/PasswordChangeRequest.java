package com.project.picngo.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * `PATCH /users/me/password` — 로그인한 사용자가 현재 비밀번호를 확인받고 새 비밀번호로 바꾼다.
 *
 * 이메일 코드로 재설정하는 `/auth/password/reset`과는 쓰임이 다르다. 그쪽은 비밀번호를
 * 잊어버린 비로그인 사용자용이고, 이건 이미 로그인한 사용자가 설정에서 바꾸는 경로다.
 */
public record PasswordChangeRequest(
        @NotBlank
        String currentPassword,

        // 가입(SignUpRequest.password)과 같은 길이 제한
        @NotBlank
        @Size(min = 8, max = 64)
        String newPassword
) {
}
