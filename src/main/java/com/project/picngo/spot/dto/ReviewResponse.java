package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Review;
import com.project.picngo.spot.domain.enums.TimePeriod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ReviewResponse(
        Long id,
        Long userId,
        Integer rating,
        String content,
        String equipmentInfo,
        TimePeriod timePeriod,
        List<String> photos,
        LocalDate visitedAt,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review) {
        return from(review, List.of());
    }

    public static ReviewResponse from(Review review, List<String> photos) {
        return new ReviewResponse(
                review.getId(),
                review.getUserId(),
                review.getRating(),
                review.getContent(),
                review.getEquipmentInfo(),
                review.getTimePeriod(),
                photos,
                review.getVisitedAt(),
                review.getCreatedAt()
        );
    }
}
