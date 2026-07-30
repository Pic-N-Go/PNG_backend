package com.project.picngo.user.dto;

import com.project.picngo.user.domain.User;

public record FollowUserResponse(Long id,
                                 String nickname,
                                 String profileImageUrl) {

    // 팔로워/팔로잉 목록에 보여줄 사용자 정보
    public static FollowUserResponse from(User user) {
        return new FollowUserResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }
}
