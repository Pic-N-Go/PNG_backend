package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Review;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long userId,
        Integer rating,
        String content,
        String equipmentInfo,
        LocalDate visitedAt,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getUserId(),
                review.getRating(),
                review.getContent(),
                review.getEquipmentInfo(),
                review.getVisitedAt(),
                review.getCreatedAt()
        );
    }
}
