package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.enums.ReviewTag;
import com.project.picngo.spot.domain.enums.TimePeriod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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
            String profileImageUrl,
            Integer rating,
            String content,
            String equipmentInfo,
            TimePeriod timePeriod,
            Set<ReviewTag> tags,
            List<ReviewPhotoResponse> photos,
            LocalDate visitedAt,
            LocalDateTime createdAt
    ) {
        public static ReviewInfo of(Review review, String nickname, String profileImageUrl, Set<ReviewTag> tags, List<ReviewPhotoResponse> photos) {
            return new ReviewInfo(
                    review.getId(),
                    review.getUserId(),
                    nickname,
                    profileImageUrl,
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
    }
}
