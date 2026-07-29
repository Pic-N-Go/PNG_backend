package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.enums.ReviewTag;
import com.project.picngo.spot.domain.enums.TimePeriod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record ReviewResponse(
        Long id,
        Long userId,
        Integer rating,
        String content,
        String equipmentInfo,
        TimePeriod timePeriod,
        Set<ReviewTag> tags,
        List<ReviewPhotoResponse> photos,
        LocalDate visitedAt,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review, Set<ReviewTag> tags, List<ReviewPhotoResponse> photos) {
        return new ReviewResponse(
                review.getId(),
                review.getUserId(),
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
