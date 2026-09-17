package com.project.picngo.external.service;

import com.project.picngo.external.dto.AirQualityResponse;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.spot.dto.CurrentWeatherResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentWeatherServiceTest {

    // 협력자는 null로 주입 — assemble()는 순수 함수라 협력자 불필요
    private final CurrentWeatherService service = new CurrentWeatherService(null, null);

    @Test
    @DisplayName("현재 시각에 가장 가까운 예보를 한글 날씨로 매핑한다")
    void mapsClosestForecast() {
        List<WeatherForecastResponse> forecasts = List.of(
                new WeatherForecastResponse("20260720", "0900", "RAINY", 20.0),
                new WeatherForecastResponse("20260720", "1500", "CLEAR", 28.0),
                new WeatherForecastResponse("20260720", "2100", "CLOUDY", 24.0)
        );
        AirQualityResponse.Item air = new AirQualityResponse.Item("중구", "25", "10", "0.03", "1", "2");
        GoldenHourResponse gh = new GoldenHourResponse(
                "2026-07-20T05:15:00+09:00", "2026-07-20T19:42:00+09:00", null, null);

        CurrentWeatherResponse r = service.assemble(
                "서울", forecasts, air, gh, null,
                LocalDate.of(2026, 7, 20), LocalTime.of(14, 30));

        assertThat(r.region()).isEqualTo("서울");
        assertThat(r.weatherStatus()).isEqualTo("맑음");     // 14:30 → 1500 슬롯
        assertThat(r.temperature()).isEqualTo(28.0);
        assertThat(r.fineDust().grade()).isEqualTo("좋음");   // pm10Grade=1
        assertThat(r.fineDust().value()).isEqualTo(25.0);
        assertThat(r.ozone().grade()).isEqualTo("보통");      // o3Grade=2
        assertThat(r.ozone().value()).isEqualTo(0.03);
    }

    @Test
    @DisplayName("골든아워는 진행 중에도 유지되고, 오늘 것이 끝나면 내일 아침을 준다")
    void nextGoldenHour() {
        // 일출 05:15 → 오전 골든아워 04:45~05:15 / 일몰 19:42 → 저녁 골든아워 19:12~19:42
        GoldenHourResponse gh = new GoldenHourResponse(
                "2026-07-20T05:15:00+09:00", "2026-07-20T19:42:00+09:00", null, null);
        GoldenHourResponse tomorrow = new GoldenHourResponse(
                "2026-07-21T05:16:00+09:00", "2026-07-21T19:41:00+09:00", null, null);

        // 04:00 → 오전 골든아워 전 → 04:45
        assertThat(service.assemble("서울", List.of(), null, gh, null,
                LocalDate.of(2026, 7, 20), LocalTime.of(4, 0)).goldenHour()).isEqualTo("04:45");

        // 05:00 → 오전 골든아워 진행 중 → 사라지지 않고 04:45 유지
        assertThat(service.assemble("서울", List.of(), null, gh, null,
                LocalDate.of(2026, 7, 20), LocalTime.of(5, 0)).goldenHour()).isEqualTo("04:45");

        // 14:00 → 오전 끝남 → 저녁 19:12
        assertThat(service.assemble("서울", List.of(), null, gh, null,
                LocalDate.of(2026, 7, 20), LocalTime.of(14, 0)).goldenHour()).isEqualTo("19:12");

        // 19:30 → 저녁 골든아워 진행 중 → 사라지지 않고 19:12 유지
        assertThat(service.assemble("서울", List.of(), null, gh, null,
                LocalDate.of(2026, 7, 20), LocalTime.of(19, 30)).goldenHour()).isEqualTo("19:12");

        // 20:00 → 일몰도 지남 → 내일 아침 골든아워(05:16-30분)
        assertThat(service.assemble("서울", List.of(), null, gh, tomorrow,
                LocalDate.of(2026, 7, 20), LocalTime.of(20, 0)).goldenHour()).isEqualTo("내일 04:46");

        // 내일 값을 못 받았으면(외부 API 실패) 지난 시각을 "다음"으로 주지 않고 null
        assertThat(service.assemble("서울", List.of(), null, gh, null,
                LocalDate.of(2026, 7, 20), LocalTime.of(20, 0)).goldenHour()).isNull();
    }

    @Test
    @DisplayName("예보 없음/미세먼지 없음이면 해당 필드는 null (부분 응답)")
    void partialNulls() {
        CurrentWeatherResponse r = service.assemble("서울", List.of(), null, null, null,
                LocalDate.of(2026, 7, 20), LocalTime.of(12, 0));

        assertThat(r.region()).isEqualTo("서울");
        assertThat(r.weatherStatus()).isNull();
        assertThat(r.temperature()).isNull();
        assertThat(r.fineDust()).isNull();
        assertThat(r.ozone()).isNull();
        assertThat(r.goldenHour()).isNull();
    }

    @Test
    @DisplayName("null 또는 파싱 불가 시간은 스킵하고 유효한 예보만 선택")
    void skipsInvalidTimeForecasts() {
        List<WeatherForecastResponse> forecasts = List.of(
                new WeatherForecastResponse("20260720", null, "RAINY", 20.0),      // null time
                new WeatherForecastResponse("20260720", "9999", "SNOWY", 15.0),    // malformed time
                new WeatherForecastResponse("20260720", "1500", "CLEAR", 28.0)     // valid
        );

        CurrentWeatherResponse r = service.assemble(
                "서울", forecasts, null, null, null,
                LocalDate.of(2026, 7, 20), LocalTime.of(14, 30));

        assertThat(r.weatherStatus()).isEqualTo("맑음");
        assertThat(r.temperature()).isEqualTo(28.0);
    }

    @Test
    @DisplayName("모든 예보가 파싱 불가이면 null 반환")
    void allInvalidTimeReturnsNull() {
        List<WeatherForecastResponse> forecasts = List.of(
                new WeatherForecastResponse("20260720", null, "RAINY", 20.0),
                new WeatherForecastResponse("20260720", "9999", "SNOWY", 15.0)
        );

        CurrentWeatherResponse r = service.assemble(
                "서울", forecasts, null, null, null,
                LocalDate.of(2026, 7, 20), LocalTime.of(14, 30));

        assertThat(r.weatherStatus()).isNull();
        assertThat(r.temperature()).isNull();
    }

    @Test
    @DisplayName("늦은 밤엔 새벽 슬롯이 아니라 절대 시간차가 가장 가까운 슬롯을 고른다")
    void lateNightPicksNearestAbsoluteSlot() {
        List<WeatherForecastResponse> forecasts = List.of(
                new WeatherForecastResponse("20260720", "0010", "RAINY", 18.0),
                new WeatherForecastResponse("20260720", "2300", "CLEAR", 24.0)
        );

        // 23:50 → 2300 슬롯(50분)이 0010 슬롯(1420분)보다 가까움. 원형거리였다면 0010(20분)을 잘못 골랐을 것.
        CurrentWeatherResponse r = service.assemble(
                "서울", forecasts, null, null, null,
                LocalDate.of(2026, 7, 20), LocalTime.of(23, 50));

        assertThat(r.weatherStatus()).isEqualTo("맑음");
        assertThat(r.temperature()).isEqualTo(24.0);
    }
}
