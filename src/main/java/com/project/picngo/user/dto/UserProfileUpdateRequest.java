package com.project.picngo.user.dto;

public record UserProfileUpdateRequest(
        String nickname,
        String profileImageUrl
) {
}