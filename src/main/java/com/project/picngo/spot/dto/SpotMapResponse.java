package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotCategory;

public record SpotMapResponse(
        Long id,
        String name,
        SpotCategory category,
        Double latitude,
        Double longitude,
        String thumbnailUrl,
        Integer photogenicScore
) {
    public static SpotMapResponse from(Spot spot) {
        return new SpotMapResponse(
                spot.getId(),
                spot.getName(),
                spot.getCategory(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getThumbnailUrl(),
                spot.getPhotogenicScore()
        );
    }
}
