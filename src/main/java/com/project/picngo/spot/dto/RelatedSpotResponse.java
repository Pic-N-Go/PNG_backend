package com.project.picngo.spot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RelatedSpotResponse(
        int rank,
        String name,
        String category,
        String region,
        Long spotId,
        String imageUrl,
        Double rating,
        Integer reviewCount,
        Double distanceKm,
        boolean matched
) {
    public static RelatedSpotResponse fromExternal(
            int rank,
            String name,
            String category,
            String region,
            Long spotId,
            String imageUrl,
            Double rating,
            Integer reviewCount,
            Double distanceKm
    ) {
        return new RelatedSpotResponse(
                rank,
                name,
                category,
                region,
                spotId,
                imageUrl,
                rating != null ? rating : 0.0,
                reviewCount != null ? reviewCount : 0,
                distanceKm,
                spotId != null
        );
    }
}
