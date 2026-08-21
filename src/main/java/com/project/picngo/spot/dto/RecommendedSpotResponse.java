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
        Integer bookmarkCount,
        Double reviewAverage,
        // 홈 추천 카드가 인기 카드와 같은 모양이어야 해서 SpotResponse와 동일 규칙으로 채운다.
        Boolean isBookmarked
) {
    public static RecommendedSpotResponse from(Spot spot, boolean isBookmarked) {
        return new RecommendedSpotResponse(
                spot.getId(),
                spot.getName(),
                spot.getAddress(),
                spot.getCategoryNames(),
                spot.getThumbnailUrl(),
                spot.getBadge(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getReviewCount(),
                spot.getBookmarkCount(),
                spot.getReviewAverage(),
                isBookmarked
        );
    }
}
