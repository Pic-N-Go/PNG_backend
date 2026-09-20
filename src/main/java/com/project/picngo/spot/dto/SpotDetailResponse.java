package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;

import java.util.List;

public record SpotDetailResponse(
        Long id,
        String name,
        String address,
        Boolean badge,
        String imageUrl,
        Double latitude,
        Double longitude,
        NavigationInfo navigation,
        List<String> categories,
        String overview,
        List<String> tags,
        List<String> reviewTags,
        ConvenienceInfo convenience,
        Boolean hasPetInfo,
        Boolean hasAccessibilityInfo,
        StatsInfo stats,
        Boolean isBookmarked,
        Long myReviewId,
        PhotoAwardRef photoAward
) {
    public record PhotoAwardRef(
            Long id,
            String title,
            String awardName,
            String photographer,
            String awardYearMonth,
            String imageUrl,
            String thumbnailUrl,
            String copyrightNotice
    ) {
        public static PhotoAwardRef from(com.project.picngo.spot.domain.PhotoAward award) {
            if (award == null) return null;
            return new PhotoAwardRef(
                    award.getId(),
                    award.getTitle(),
                    award.getAwardName(),
                    award.getPhotographer(),
                    award.getAwardYearMonth(),
                    award.getImageUrl(),
                    award.getThumbnailUrl(),
                    "출처: ⓒ한국관광공사"
            );
        }
    }

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
            List<String> reviewTags,
            Double avgRating,
            Integer reviewCount,
            Long photoCount,
            Boolean isBookmarked,
            Long myReviewId,
            boolean hasPetInfo,
            boolean hasAccessibilityInfo,
            PhotoAwardRef photoAward
    ) {
        List<String> categoryNames = spot.getCategoryNames();
        return new SpotDetailResponse(
                spot.getId(),
                spot.getName(),
                spot.getAddress(),
                spot.getBadge(),
                spot.getImageUrl(),
                spot.getLatitude(),
                spot.getLongitude(),
                NavigationInfo.of(spot),
                categoryNames,
                spot.getOverview(),
                categoryNames,
                reviewTags,
                ConvenienceInfo.from(spot),
                hasPetInfo,
                hasAccessibilityInfo,
                new StatsInfo(avgRating, reviewCount, photoCount),
                isBookmarked,
                myReviewId,
                photoAward
        );
    }
}
