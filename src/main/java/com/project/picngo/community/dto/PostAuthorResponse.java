package com.project.picngo.community.dto;

import com.project.picngo.user.domain.User;

public record PostAuthorResponse(
        Long id,
        String nickname,
        String profileImageUrl
) {
    public static PostAuthorResponse from(User user, String profileImageUrl) {
        return new PostAuthorResponse(user.getId(), user.getNickname(), profileImageUrl);
    }
}
