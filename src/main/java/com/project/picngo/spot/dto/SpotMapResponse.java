package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;

import java.util.List;

public record SpotMapResponse ( Long id,
                                String name,
                                List<String> categories,
                                Double latitude,
                                Double longitude,
                                String thumbnailUrl,
                                Integer photogenicScore,
                                Boolean badge){
    public static SpotMapResponse from(Spot spot){
        return new SpotMapResponse(
                spot.getId(),
                spot.getName(),
                spot.getCategoryNames(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getThumbnailUrl(),
                spot.getPhotogenicScore(),
                spot.getBadge()
        );
    }
}
