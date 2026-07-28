package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotSource;

import java.util.List;

public record SpotResponse(
        Long id,
        String name,
        String address,
        String zipcode,
        String overview,
        Double latitude,
        Double longitude,
        List<String> categories,
        SpotSource source,
        Boolean badge,
        String imageUrl,
        String thumbnailUrl,
        Integer bookmarkCount,
        Integer reviewCount,
        Integer photogenicScore,
        Double reviewAverage
) {
    public static SpotResponse from(Spot spot) {
        return new SpotResponse(
                spot.getId(),
                spot.getName(),
                spot.getAddress(),
                spot.getZipcode(),
                spot.getOverview(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getCategoryNames(),
                spot.getSource(),
                spot.getBadge(),
                spot.getImageUrl(),
                spot.getThumbnailUrl(),
                spot.getBookmarkCount(),
                spot.getReviewCount(),
                spot.getPhotogenicScore(),
                spot.getReviewAverage()
        );
    }
}
