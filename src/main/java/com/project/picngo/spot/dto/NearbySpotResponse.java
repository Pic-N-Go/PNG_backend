package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;

import java.util.List;

public record NearbySpotResponse(
        Long id,
        String name,
        String address,
        List<String> categories,
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
                spot.getCategories().stream().map(Enum::name).toList(),
                spot.getThumbnailUrl(),
                spot.getBadge(),
                spot.getLatitude(),
                spot.getLongitude(),
                Math.round(distanceKm * 10.0) / 10.0
        );
    }
}
