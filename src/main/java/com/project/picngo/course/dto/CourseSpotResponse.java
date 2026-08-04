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
        Integer travelTimeMinutes,
        // true면 카카오 실측이 아닌 자체 추정값. 프론트는 "약 35분"처럼 근사치임을 드러낸다
        Boolean travelTimeEstimated
) {}
