package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;

import java.util.List;

public record RecommendedSpotResponse(
        Long id,
        String name,
        String address,
        List<String> categories,
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
                spot.getCategories().stream().map(Enum::name).toList(),
                spot.getThumbnailUrl(),
                spot.getBadge(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getReviewCount(),
                spot.getBookmarkCount()
        );
    }
}
