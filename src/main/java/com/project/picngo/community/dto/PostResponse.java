package com.project.picngo.community.dto;

import com.project.picngo.community.domain.CommunityWeather;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record PostResponse(
        Long id,
        String content,
        Long spotId,
        String spotName,
        LocalTime shootingTime,
        CommunityWeather weather,
        String cameraModel,
        String lensModel,
        List<String> tags,
        PostAuthorResponse author,
        List<PostImageResponse> images,
        long likeCount,
        long commentCount,
        long bookmarkCount,
        boolean liked,
        boolean bookmarked,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
