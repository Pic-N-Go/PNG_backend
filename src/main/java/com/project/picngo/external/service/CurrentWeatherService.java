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

        // 오늘 일몰이 지난 뒤에만 내일 값을 받는다 — 안 그러면 하루 종일 외부 API를 두 번씩 부른다.
        SunTimes todayTimes = sunTimes(goldenHour);
        GoldenHourResponse tomorrowGolden = (todayTimes != null && !now.isBefore(todayTimes.sunset()))
                ? safe(() -> weatherCacheService.getCachedGoldenHour(lat, lng, today.plusDays(1).toString()))
                : null;

        return assemble(sido, forecasts, air, goldenHour, tomorrowGolden, today, now);
    }

    // 순수 함수 — 테스트 대상
    CurrentWeatherResponse assemble(String region,
                                    List<WeatherForecastResponse> forecasts,
                                    AirQualityResponse.Item air,
                                    GoldenHourResponse goldenHour,
                                    GoldenHourResponse tomorrowGolden,
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

        String goldenHourStr = nextGoldenHour(goldenHour, tomorrowGolden, now);

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

    /** 골든아워 판정에 필요한 오늘의 시각들. 하나라도 파싱이 안 되면 null. */
    private record SunTimes(LocalTime morningGolden, LocalTime sunrise,
                            LocalTime eveningGolden, LocalTime sunset) {}

    private SunTimes sunTimes(GoldenHourResponse gh) {
        if (gh == null || gh.sunriseTime() == null || gh.sunsetTime() == null) return null;
        try {
            LocalTime sunrise = OffsetDateTime.parse(gh.sunriseTime()).atZoneSameInstant(KST).toLocalTime();
            LocalTime sunset = OffsetDateTime.parse(gh.sunsetTime()).atZoneSameInstant(KST).toLocalTime();
            return new SunTimes(sunrise.minusMinutes(30), sunrise, sunset.minusMinutes(30), sunset);
        } catch (Exception e) {
            log.warn("골든아워 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 현재 시각 기준 다음 골든아워(일출-30분 / 일몰-30분).
     *
     * 시작 시각이 아니라 끝나는 시각(일출·일몰)을 기준으로 넘긴다 — 시작 시각으로 자르면
     * 정작 골든아워가 진행 중인 30분 동안 화면에서 사라진다.
     * 오늘 것이 다 끝났으면 내일 아침을 "내일 HH:mm"으로 준다.
     */
    private String nextGoldenHour(GoldenHourResponse todayGh, GoldenHourResponse tomorrowGh, LocalTime now) {
        SunTimes t = sunTimes(todayGh);
        if (t == null) return null;
        if (now.isBefore(t.sunrise())) return t.morningGolden().format(HH_MM);
        if (now.isBefore(t.sunset())) return t.eveningGolden().format(HH_MM);

        SunTimes tomorrow = sunTimes(tomorrowGh);
        return tomorrow == null ? null : "내일 " + tomorrow.morningGolden().format(HH_MM);
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
