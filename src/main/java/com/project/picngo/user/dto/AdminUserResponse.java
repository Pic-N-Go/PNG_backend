package com.project.picngo.user.dto;

import com.project.picngo.user.domain.Role;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "관리자용 회원 정보 응답 DTO")
public record AdminUserResponse(
        @Schema(description = "회원 ID", example = "1")
        Long id,

        @Schema(description = "이메일", example = "user@example.com")
        String email,

        @Schema(description = "닉네임", example = "사진작가")
        String nickname,

        @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.jpg")
        String profileImageUrl,

        @Schema(description = "권한 (USER / ADMIN)", example = "USER")
        Role role,

        @Schema(description = "소셜 제공자 (LOCAL / KAKAO)", example = "LOCAL")
        SocialProvider provider,

        @Schema(description = "가입 일시")
        LocalDateTime createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getRole(),
                user.getProvider(),
                user.getCreatedAt()
        );
    }
}
