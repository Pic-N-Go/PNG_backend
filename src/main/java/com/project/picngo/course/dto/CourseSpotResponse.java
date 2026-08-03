package com.project.picngo.course.dto;

import com.project.picngo.spot.dto.NavigationInfo;

import java.util.List;

public record CourseSpotResponse(
        Long id,
        Long spotId,
        String spotName,
        Double latitude,
        Double longitude,
        NavigationInfo navigation,
        List<String> categories,
        String thumbnailUrl,
        Integer photogenicScore,
        Integer dayNumber,
        Integer sequenceOrder,
        String memo,
        Integer travelTimeMinutes
) {}
