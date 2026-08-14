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
        Double reviewAverage,
        // 1개 이상 컬렉션에 담겨 있으면 true. 비로그인 조회는 항상 false (SpotDetailResponse와 동일 규칙).
        Boolean isBookmarked
) {
    /**
     * isBookmarked는 호출부가 반드시 정해서 넘긴다 — 기본값 false를 주는 오버로드를 두면
     * 로그인 유저에게 false를 흘리고도 컴파일이 통과한다. 모르는 경로면 명시적으로 false를 넘길 것.
     */
    public static SpotResponse from(Spot spot, boolean isBookmarked) {
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
                spot.getReviewAverage(),
                isBookmarked
        );
    }
}
