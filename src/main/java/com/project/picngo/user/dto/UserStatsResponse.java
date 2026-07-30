package com.project.picngo.user.dto;

public record UserStatsResponse (
        long follwerCount,
        long followingCount,
        long reviewCount,
        long visitedSpotCount
){
}
