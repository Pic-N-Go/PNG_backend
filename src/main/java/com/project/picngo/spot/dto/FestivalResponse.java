package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Builder
public class FestivalResponse {

    private Long id;
    private String name;
    private String address;
    private String imageUrl;
    private String thumbnailUrl;
    private LocalDate eventStartDate;
    private LocalDate eventEndDate;
    private String progressStatus; // ONGOING, UPCOMING, ENDED, UNKNOWN
    private String overview;
    private Double latitude;
    private Double longitude;
    private Set<String> categories;
    private String usetime;
    private String parking;
    private String infocenter;

    public static FestivalResponse from(Spot spot) {
        return from(spot, LocalDate.now());
    }

    public static FestivalResponse from(Spot spot, LocalDate currentDate) {
        String progressStatus = calculateProgressStatus(spot.getEventStartDate(), spot.getEventEndDate(), currentDate);

        return FestivalResponse.builder()
                .id(spot.getId())
                .name(spot.getName())
                .address(spot.getAddress())
                .imageUrl(spot.getImageUrl())
                .thumbnailUrl(spot.getThumbnailUrl())
                .eventStartDate(spot.getEventStartDate())
                .eventEndDate(spot.getEventEndDate())
                .progressStatus(progressStatus)
                .overview(spot.getOverview())
                .latitude(spot.getLatitude())
                .longitude(spot.getLongitude())
                .categories(spot.getCategories() != null
                        ? spot.getCategories().stream().map(Enum::name).collect(Collectors.toSet())
                        : Set.of())
                .usetime(spot.getUsetime())
                .parking(spot.getParking())
                .infocenter(spot.getInfocenter())
                .build();
    }

    private static String calculateProgressStatus(LocalDate start, LocalDate end, LocalDate current) {
        if (start == null && end == null) {
            return "UNKNOWN";
        }
        if (start != null && current.isBefore(start)) {
            return "UPCOMING";
        }
        if (end != null && current.isAfter(end)) {
            return "ENDED";
        }
        return "ONGOING";
    }
}
