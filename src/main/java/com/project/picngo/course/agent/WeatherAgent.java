package com.project.picngo.course.agent;

import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.external.service.WeatherCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * [워커 1: 날씨/골든아워 전문 에이전트]
 * 대상 지역의 기상청 예보 및 일출/일몰 골든아워 정보를 수집하여 출사 시간대 가이드를 제공합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherAgent {

    private final WeatherCacheService weatherCacheService;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public record WeatherBrief(
            String weatherSummary,
            String goldenHourTime,
            String sunsetTime
    ) {}

    public WeatherBrief analyzeWeather(Double lat, Double lng, LocalDate targetDate) {
        if (lat == null || lng == null || targetDate == null) {
            return new WeatherBrief("날씨 정보 없음", "정보 없음", "정보 없음");
        }

        String weatherStatus = "맑음";
        try {
            String dateStr = targetDate.format(DATE_FMT);
            List<WeatherForecastResponse> forecasts = weatherCacheService.getCached7DayForecast(lat, lng, dateStr);
            if (forecasts != null && !forecasts.isEmpty()) {
                weatherStatus = forecasts.stream()
                        .filter(f -> dateStr.equals(f.date()))
                        .map(WeatherForecastResponse::weatherStatus)
                        .findFirst()
                        .orElse("맑음");
            }
        } catch (Exception e) {
            log.warn("WeatherAgent 예보 조회 실패: {}", e.getMessage());
        }

        String sunset = "18:30";
        String goldenHour = "18:00 ~ 19:00";
        try {
            GoldenHourResponse gh = weatherCacheService.getCachedGoldenHour(lat, lng, targetDate.toString());
            if (gh != null) {
                if (gh.sunsetTime() != null) sunset = gh.sunsetTime();
                if (gh.goldenHourEvening() != null) goldenHour = gh.goldenHourEvening();
            }
        } catch (Exception e) {
            log.warn("WeatherAgent 골든아워 조회 실패: {}", e.getMessage());
        }

        return new WeatherBrief(weatherStatus, goldenHour, sunset);
    }
}
