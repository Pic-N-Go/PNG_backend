package com.project.picngo.course.dto;

import java.time.LocalDate;

public record CourseWeatherResponse(
        Integer dayNumber,
        LocalDate date,
        Long targetSpotId,
        String targetSpotName,
        String weatherStatus,
        Integer temperature,
        String sunsetTime,
        String goldenHourEvening
) {}
