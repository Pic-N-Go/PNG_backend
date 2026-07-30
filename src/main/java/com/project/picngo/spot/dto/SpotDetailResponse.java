package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotTag;

import java.util.List;

public record SpotDetailResponse(
        Long id,
        String name,
        String address,
        Boolean badge,
        String imageUrl,
        Double latitude,
        Double longitude,
        List<String> categories,
        String overview,
        List<String> tags,
        List<String> reviewTags,
        ConvenienceInfo convenience,
        StatsInfo stats,
        List<String> checklist,
        Boolean isBookmarked,
        Long myReviewId
) {
    public record ConvenienceInfo(
            String parking,
            String wheelchairAccess,
            String strollerAccess,
            String petFriendly,
            String subwayAccess,
            String usetime,
            String restdate,
            String infocenter
    ) {
        public static ConvenienceInfo from(Spot spot) {
            return new ConvenienceInfo(
                    blankToNull(spot.getParking()),
                    blankToNull(spot.getWheelchairAccess()),
                    blankToNull(spot.getStrollerAccess()),
                    blankToNull(spot.getPetFriendly()),
                    blankToNull(spot.getSubwayAccess()),
                    blankToNull(spot.getUsetime()),
                    blankToNull(spot.getRestdate()),
                    blankToNull(spot.getInfocenter())
            );
        }

        // "정보 없음"은 null로 통일. 빈 문자열/공백은 TourAPI 미제공 → null. "없음"/"불가" 등 실데이터는 그대로 유지.
        private static String blankToNull(String value) {
            return (value == null || value.isBlank()) ? null : value;
        }
    }

    public record StatsInfo(
            Double avgRating,
            Integer reviewCount,
            Long photoCount
    ) {}

    public static SpotDetailResponse of(
            Spot spot,
            List<SpotTag> tags,
            List<String> reviewTags,
            List<String> checklist,
            Double avgRating,
            Integer reviewCount,
            Long photoCount,
            Boolean isBookmarked,
        Long myReviewId
    ) {
        return new SpotDetailResponse(
                spot.getId(),
                spot.getName(),
                spot.getAddress(),
                spot.getBadge(),
                spot.getImageUrl(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getCategoryNames(),
                spot.getOverview(),
                tags.stream().map(SpotTag::getTag).toList(),
                reviewTags,
                ConvenienceInfo.from(spot),
                new StatsInfo(avgRating, reviewCount, photoCount),
                checklist,
                isBookmarked,
                myReviewId
        );
    }
}
