package com.project.picngo.course.dto;

import java.util.List;

public record CourseSpotResponse(
        Long id,
        Long spotId,
        String spotName,
        Double latitude,
        Double longitude,
        List<String> categories,
        String thumbnailUrl,
        Integer photogenicScore,
        Integer dayNumber,
        Integer sequenceOrder,
        String memo,
        Integer travelTimeMinutes
) {}
