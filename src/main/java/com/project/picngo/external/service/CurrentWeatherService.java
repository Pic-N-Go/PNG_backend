package com.project.picngo.external.service;

import com.project.picngo.external.KakaoRegionClient;
import com.project.picngo.external.SidoNameMapper;
import com.project.picngo.external.dto.AirQualityResponse;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.spot.dto.CurrentWeatherResponse;
import com.project.picngo.spot.dto.CurrentWeatherResponse.AirGrade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentWeatherService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final KakaoRegionClient kakaoRegionClient;
    private final WeatherCacheService weatherCacheService;

    public CurrentWeatherResponse getCurrentWeather(Double lat, Double lng) {
        LocalDate today = LocalDate.now(KST);
        LocalTime now = LocalTime.now(KST);

        String region1depth = kakaoRegionClient.coord2region(lat, lng);
        String sido = SidoNameMapper.normalize(region1depth);

        List<WeatherForecastResponse> forecasts = safe(() ->
                weatherCacheService.getCached7DayForecast(lat, lng, today.format(DATE_FMT)));
        GoldenHourResponse goldenHour = safe(() ->
                weatherCacheService.getCachedGoldenHour(lat, lng, today.toString()));
        AirQualityResponse.Item air = sido == null ? null :
                safe(() -> weatherCacheService.getCachedAirQuality(sido));

        return assemble(sido, forecasts, air, goldenHour, today, now);
    }

    // 순수 함수 — 테스트 대상
    CurrentWeatherResponse assemble(String region,
                                    List<WeatherForecastResponse> forecasts,
                                    AirQualityResponse.Item air,
                                    GoldenHourResponse goldenHour,
                                    LocalDate today, LocalTime now) {
        String status = null;
        Double temperature = null;
        WeatherForecastResponse closest = closestForecast(forecasts, today, now);
        if (closest != null) {
            status = weatherLabel(closest.weatherStatus());
            temperature = closest.temperature();
        }

        AirGrade fineDust = air == null ? null
                : new AirGrade(gradeLabel(air.pm10Grade()), parseDouble(air.pm10Value()));
        AirGrade ozone = air == null ? null
                : new AirGrade(gradeLabel(air.o3Grade()), parseDouble(air.o3Value()));

        String goldenHourStr = nextGoldenHour(goldenHour, now);

        return new CurrentWeatherResponse(region, status, temperature, fineDust, ozone, goldenHourStr);
    }

    private WeatherForecastResponse closestForecast(List<WeatherForecastResponse> forecasts,
                                                    LocalDate today, LocalTime now) {
        if (forecasts == null || forecasts.isEmpty()) return null;
        String dateStr = today.format(DATE_FMT);
        return forecasts.stream()
                .filter(f -> dateStr.equals(f.date()))
                .filter(f -> canParseTime(f.time()))
                // "현재 날씨"라 오늘 슬롯 중 현재 시각과 절대 시간차가 가장 작은 것 선택 (원형 거리 X)
                .min(Comparator.comparingLong(f ->
                        Math.abs(Duration.between(LocalTime.parse(f.time(), HHMM), now).toMinutes())))
                .orElse(null);
    }

    private boolean canParseTime(String timeStr) {
        if (timeStr == null) return false;
        try {
            LocalTime.parse(timeStr, HHMM);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 현재 시각 기준 다음 골든아워(일출-30분 / 일몰-30분). 오늘 둘 다 지났으면 null.
    private String nextGoldenHour(GoldenHourResponse gh, LocalTime now) {
        if (gh == null || gh.sunriseTime() == null || gh.sunsetTime() == null) return null;
        try {
            LocalTime sunrise = OffsetDateTime.parse(gh.sunriseTime()).atZoneSameInstant(KST).toLocalTime();
            LocalTime sunset = OffsetDateTime.parse(gh.sunsetTime()).atZoneSameInstant(KST).toLocalTime();
            LocalTime morning = sunrise.minusMinutes(30);
            LocalTime evening = sunset.minusMinutes(30);
            if (now.isBefore(morning)) return morning.format(HH_MM);
            if (now.isBefore(evening)) return evening.format(HH_MM);
            // ponytail: 오늘 골든아워가 다 지나면 null. 내일 일출 값을 주려면 getCachedGoldenHour를
            // 내일 날짜로 한 번 더 호출해야 하므로, 프론트에 "오늘 종료" 표시가 필요해지면 그때 추가.
            return null;
        } catch (Exception e) {
            log.warn("골든아워 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private String weatherLabel(String status) {
        if (status == null) return null;
        return switch (status) {
            case "CLEAR"  -> "맑음";
            case "CLOUDY" -> "흐림";
            case "RAINY"  -> "비";
            case "SNOWY"  -> "눈";
            default        -> null;
        };
    }

    private String gradeLabel(String grade) {
        if (grade == null) return null;
        return switch (grade) {
            case "1" -> "좋음";
            case "2" -> "보통";
            case "3" -> "나쁨";
            case "4" -> "매우나쁨";
            default   -> null;
        };
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank() || value.equals("-")) return null;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { return null; }
    }

    private <T> T safe(java.util.function.Supplier<T> supplier) {
        try { return supplier.get(); }
        catch (Exception e) { log.warn("날씨 데이터 조회 실패: {}", e.getMessage()); return null; }
    }
}
