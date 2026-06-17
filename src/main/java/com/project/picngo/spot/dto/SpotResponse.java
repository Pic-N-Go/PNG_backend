package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotCategory;

public record SpotResponse(
        Long id,
        String name,
        SpotCategory category,
        String address,
        Double latitude,
        Double longitude,
        String thumbnailUrl,
        Integer bookmarkCount,
        Integer reviewCount,
        Integer photogenicScore
) {
    public static SpotResponse from(Spot spot) {
        return new SpotResponse(
                spot.getId(),
                spot.getName(),
                spot.getCategory(),
                spot.getAddress(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getThumbnailUrl(),
                spot.getBookmarkCount(),
                spot.getReviewCount(),
                spot.getPhotogenicScore()
        );
    }
}
