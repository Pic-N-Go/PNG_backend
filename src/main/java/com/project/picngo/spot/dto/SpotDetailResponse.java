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
        String category,
        String overview,
        List<String> tags,
        ConvenienceInfo convenience,
        StatsInfo stats,
        List<String> checklist,
        Boolean isBookmarked
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
                    spot.getParking(),
                    spot.getWheelchairAccess(),
                    spot.getStrollerAccess(),
                    spot.getPetFriendly(),
                    spot.getSubwayAccess(),
                    spot.getUsetime(),
                    spot.getRestdate(),
                    spot.getInfocenter()
            );
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
            List<String> checklist,
            Double avgRating,
            Integer reviewCount,
            Long photoCount,
            Boolean isBookmarked
    ) {
        return new SpotDetailResponse(
                spot.getId(),
                spot.getName(),
                spot.getAddress(),
                spot.getBadge(),
                spot.getImageUrl(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getCategory().name(),
                spot.getOverview(),
                tags.stream().map(SpotTag::getTag).toList(),
                ConvenienceInfo.from(spot),
                new StatsInfo(avgRating, reviewCount, photoCount),
                checklist,
                isBookmarked
        );
    }
}
