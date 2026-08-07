package com.project.picngo.external.dto;

public record GoldenHourResponse(
        String sunriseTime,
        String sunsetTime,
        String goldenHourMorning,
        String goldenHourEvening
) {}
