package com.project.picngo.user.dto;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.user.domain.User;

import java.util.Set;

public record UserProfileResponse (
        Long id,
        String nickname,
        String profileImageUrl,
        String bio,
        Set<SpotCategory> spotCategories,
        long followerCount,
        long followingCount
){
    /**
     * 팔로워·팔로잉 수를 프로필에 함께 담는다. 목록 API로 세면 클라이언트가 팔로워 전체를
     * 받아야 하고, /users/me/stats는 본인 것만 되므로 남의 프로필에서는 쓸 수 없다.
     */
    public static UserProfileResponse from(User user, long followerCount, long followingCount){
        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getBio(),
                user.getSpotCategories(),
                followerCount,
                followingCount
        );
    }
}
