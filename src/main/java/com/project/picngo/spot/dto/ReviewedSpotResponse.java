package com.project.picngo.spot.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PIC MAP(마이페이지 지도)의 리뷰 핀 하나. 좌표가 목적이라 MyReviewListResponse와 따로 둔다 —
 * 지도는 핀을 한 번에 다 받아야 해 페이징이 없고, 리뷰 본문·태그·장비·사진을 쓰지 않는다.
 * 특히 photos를 같이 내리면 사진 수만큼 presigned URL 서명이 헛돌고 응답만 수십 KB 커진다.
 *
 * @param imageUrl   thumbnailUrl → imageUrl 폴백. TourAPI는 이미지가 없을 때 null이 아니라 빈
 *                   문자열을 주고 그대로 저장돼 있어, 프론트 폴백(그라디언트)이 도는 null로 내린다.
 * @param reviewedAt 리뷰 작성 시각(Review.createdAt). 사용자가 입력한 방문일(visitedAt)이 아니다 —
 *                   지도는 방문이 아니라 리뷰를 기준으로 표기한다.
 * @param rating     내가 준 별점 1~5. 스팟의 photogenicScore가 아니다.
 * @param categories SpotCategory enum 이름. 라벨(해변/야경)은 프론트가 갖는다.
 *                   Spot.getCategoryNames()와 같은 규칙 — 이름 정렬, 비어 있으면 ["ETC"].
 */
public record ReviewedSpotResponse(
        Long spotId,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String imageUrl,
        LocalDateTime reviewedAt,
        Integer rating,
        List<String> categories
) {
    /**
     * JPQL constructor projection 전용. 컬렉션은 projection에 실을 수 없어
     * categories를 비워 두고 만들고, 서비스가 별도 조회로 채운다(withCategories).
     */
    public ReviewedSpotResponse(
            Long spotId, String name, String address,
            Double latitude, Double longitude, String imageUrl,
            LocalDateTime reviewedAt, Integer rating
    ) {
        this(spotId, name, address, latitude, longitude, imageUrl, reviewedAt, rating, List.of());
    }

    public ReviewedSpotResponse withCategories(List<String> categories) {
        return new ReviewedSpotResponse(
                spotId, name, address, latitude, longitude, imageUrl, reviewedAt, rating, categories);
    }
}
