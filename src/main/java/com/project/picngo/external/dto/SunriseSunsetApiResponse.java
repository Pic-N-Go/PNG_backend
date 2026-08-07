package com.project.picngo.external.dto;

public record SunriseSunsetApiResponse(
        Results results,
        String status
) {
    public record Results(
            String sunrise,
            String sunset,
            String solar_noon
    ) {}
}
