package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.ReviewTag;
import com.project.picngo.spot.domain.enums.TimePeriod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

// 페이징 필드 이름은 GET /spots/{id}/reviews 의 PageInfo와 동일하게 맞춘다 (프론트 페이지네이션 로직 재사용)
public record MyReviewListResponse(
        List<MyReviewInfo> content,
        Long totalElements,
        Integer totalPages,
        Integer number
) {
    public record MyReviewInfo(
            Long reviewId,
            Long spotId,
            String spotName,
            String spotImageUrl,
            Integer rating,
            String content,
            String equipmentInfo,
            TimePeriod timePeriod,
            Set<ReviewTag> tags,
            List<ReviewPhotoResponse> photos,
            LocalDate visitedAt,
            LocalDateTime createdAt
    ) {
        public static MyReviewInfo of(Review review, Set<ReviewTag> tags, List<ReviewPhotoResponse> photos) {
            Spot spot = review.getSpot();
            return new MyReviewInfo(
                    review.getId(),
                    spot.getId(),
                    spot.getName(),
                    firstNonBlank(spot.getThumbnailUrl(), spot.getImageUrl()),
                    review.getRating(),
                    review.getContent(),
                    review.getEquipmentInfo(),
                    review.getTimePeriod(),
                    tags,
                    photos,
                    review.getVisitedAt(),
                    review.getCreatedAt()
            );
        }

        // TourAPI는 이미지가 없을 때 null이 아니라 빈 문자열을 주고, 그대로 저장돼 있다.
        // 프론트 폴백(그라디언트)이 null 기준이라 빈 문자열은 null로 내린다.
        private static String firstNonBlank(String first, String second) {
            if (first != null && !first.isBlank()) return first;
            return (second != null && !second.isBlank()) ? second : null;
        }
    }
}
