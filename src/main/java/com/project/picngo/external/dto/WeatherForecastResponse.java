package com.project.picngo.external.dto;

public record WeatherForecastResponse(
        String date,
        String time,
        String weatherStatus, // e.g., CLEAR, RAIN
        Double temperature
) {}
