package com.project.picngo.user.dto;

public record UserStatsResponse(
        long followerCount,
        long followingCount,
        long reviewCount,
        long visitedSpotCount
) {
}
