package com.project.picngo.user.dto;

public record UserStatsResponse(
        long followerCount,
        long followingCount,
        long reviewCount,
        long visitedSpotCount,
        // 내가 쓴 커뮤니티 게시글 수. 마이페이지 '글' 타일이 쓴다.
        long postCount
) {
}
