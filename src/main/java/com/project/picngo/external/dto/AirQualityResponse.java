package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AirQualityResponse(Response response) {

    public record Response(Body body) {}

    public record Body(List<Item> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String stationName,
            String pm10Value,   // 미세먼지 (PM10) µg/m³
            String pm25Value,   // 초미세먼지 (PM2.5) µg/m³
            String o3Value,     // 오존 ppm
            String pm10Grade,   // 1=좋음 2=보통 3=나쁨 4=매우나쁨
            String o3Grade
    ) {}
}
