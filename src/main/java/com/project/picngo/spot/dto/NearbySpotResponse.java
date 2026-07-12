package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;

public record NearbySpotResponse(
        Long id,
        String name,
        String address,
        String category,
        String thumbnailUrl,
        Boolean badge,
        Double latitude,
        Double longitude,
        Double distanceKm
) {
    public static NearbySpotResponse of(Spot spot, Double distanceKm) {
        return new NearbySpotResponse(
                spot.getId(),
                spot.getName(),
                spot.getAddress(),
                spot.getCategory().name(),
                spot.getThumbnailUrl(),
                spot.getBadge(),
                spot.getLatitude(),
                spot.getLongitude(),
                Math.round(distanceKm * 10.0) / 10.0
        );
    }
}
