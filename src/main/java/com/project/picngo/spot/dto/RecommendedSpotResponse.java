package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;

public record RecommendedSpotResponse(
        Long id,
        String name,
        String address,
        String category,
        String thumbnailUrl,
        Boolean badge,
        Double latitude,
        Double longitude,
        Integer reviewCount,
        Integer bookmarkCount
) {
    public static RecommendedSpotResponse from(Spot spot) {
        return new RecommendedSpotResponse(
                spot.getId(),
                spot.getName(),
                spot.getAddress(),
                spot.getCategory().name(),
                spot.getThumbnailUrl(),
                spot.getBadge(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getReviewCount(),
                spot.getBookmarkCount()
        );
    }
}
