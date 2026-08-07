package com.project.picngo.spot.dto;

public record CurrentWeatherResponse(
        String region,
        String weatherStatus,
        Double temperature,
        AirGrade fineDust,
        AirGrade ozone,
        String goldenHour
) {
    public record AirGrade(String grade, Double value) {}
}
