package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotCategory;

public record SpotSummaryResponse(
        Long id,
        String name,
        SpotCategory category,
        String address,
        Double latitude,
        Double longitude,
        String thumbnailUrl,
        String description,
        Integer bookmarkCount,
        Integer reviewCount,
        Integer photogenicScore
) {
    public static SpotSummaryResponse from(Spot spot) {
        return new SpotSummaryResponse(
                spot.getId(),
                spot.getName(),
                spot.getCategory(),
                spot.getAddress(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getThumbnailUrl(),
                spot.getDescription(),
                spot.getBookmarkCount(),
                spot.getReviewCount(),
                spot.getPhotogenicScore()
        );
    }
}
