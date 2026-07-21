package com.project.picngo.course.dto;

import java.time.LocalDate;

public record CourseWeatherResponse(
        Integer dayNumber,
        LocalDate date,
        Long targetSpotId,
        String targetSpotName,
        WeatherDetail morning,
        WeatherDetail afternoon,
        WeatherDetail evening,
        String sunsetTime,
        String goldenHourEvening,
        String fineDustStatus
) {}
