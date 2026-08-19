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
        long followingCount,
        /** 탈퇴 계정이면 true. 클라이언트가 팔로우 버튼·게시글 탭을 숨기는 데 쓴다. */
        boolean withdrawn
){
    /**
     * 팔로워·팔로잉 수를 프로필에 함께 담는다. 목록 API로 세면 클라이언트가 팔로워 전체를
     * 받아야 하고, /users/me/stats는 본인 것만 되므로 남의 프로필에서는 쓸 수 없다.
     */
    public static UserProfileResponse from(User user, String profileImageUrl, long followerCount, long followingCount){
        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                profileImageUrl,
                user.getBio(),
                user.getSpotCategories(),
                followerCount,
                followingCount,
                false
        );
    }

    /**
     * 탈퇴 계정의 프로필. 404로 막지 않는 이유는 게시글·댓글의 작성자 탭으로 여기에 닿을 수
     * 있고, 404면 "없는 사용자"와 구별되지 않아 오류로 읽히기 때문이다.
     *
     * 자기소개·관심테마·팔로워 수는 담지 않는다 — 파기 전이라 DB에는 남아 있지만
     * 남에게 보여줄 정보가 아니다.
     */
    public static UserProfileResponse withdrawn(User user){
        return new UserProfileResponse(
                user.getId(),
                User.WITHDRAWN_DISPLAY_NAME,
                null,
                null,
                Set.of(),
                0,
                0,
                true
        );
    }
}
