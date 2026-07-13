package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.ReviewPhoto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ReviewListResponse(
        SummaryInfo summary,
        PageInfo reviews
) {
    public record SummaryInfo(
            Double avgRating,
            Long totalCount,
            Map<Integer, Long> distribution
    ) {}

    public record PageInfo(
            List<ReviewInfo> content,
            Long totalElements,
            Integer totalPages,
            Integer number
    ) {}

    public record ReviewInfo(
            Long id,
            Long userId,
            String nickname,
            Integer rating,
            String content,
            String equipmentInfo,
            List<String> photos,
            LocalDate visitedAt,
            LocalDateTime createdAt
    ) {
        public static ReviewInfo of(Review review, String nickname, List<ReviewPhoto> photos) {
            return new ReviewInfo(
                    review.getId(),
                    review.getUserId(),
                    nickname,
                    review.getRating(),
                    review.getContent(),
                    review.getEquipmentInfo(),
                    photos.stream().map(ReviewPhoto::getPhotoUrl).toList(),
                    review.getVisitedAt(),
                    review.getCreatedAt()
            );
        }
    }
}
