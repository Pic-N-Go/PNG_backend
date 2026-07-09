package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotCategory;

public record SpotSummaryResponse(
        Long id,
        String name,
        String address,
        SpotCategory category,
        Double latitude,
        Double longitude,
        String thumbnailUrl,
        Integer photogenicScore,
        Double reviewAverage,
        Integer bookmarkCount,
        Boolean badge
) {
    public static SpotSummaryResponse from(Spot spot) {
        return new SpotSummaryResponse(
                spot.getId(),
                spot.getName(),
                spot.getAddress(),
                spot.getCategory(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getThumbnailUrl(),
                spot.getPhotogenicScore(),
                spot.getReviewAverage(),
                spot.getBookmarkCount(),
                spot.getBadge()
        );
    }
}
