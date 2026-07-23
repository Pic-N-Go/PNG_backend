package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;

import java.util.List;

public record SpotSummaryResponse(
        Long id,
        String name,
        String address,
        List<String> categories,
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
                spot.getCategoryNames(),
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
