package com.project.picngo.course.dto;

public record CourseSpotResponse(
        Long id,
        Long spotId,
        String spotName,
        Double latitude,
        Double longitude,
        String category,
        String thumbnailUrl,
        Integer photogenicScore,
        Integer dayNumber,
        Integer sequenceOrder,
        String memo,
        Integer travelTimeMinutes
) {}
