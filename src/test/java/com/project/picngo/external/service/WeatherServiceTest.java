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

class WeatherServiceTest {

    // 협력자는 null로 주입 — assemble()는 순수 함수라 협력자 불필요
    private final WeatherService service = new WeatherService(null, null);

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
                "서울", forecasts, air, gh,
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
    @DisplayName("오전 골든아워 전이면 오전(일출-30분), 지났으면 저녁(일몰-30분)")
    void nextGoldenHour() {
        GoldenHourResponse gh = new GoldenHourResponse(
                "2026-07-20T05:15:00+09:00", "2026-07-20T19:42:00+09:00", null, null);

        // 04:00 → 오전 골든아워(04:45) 전 → 04:45
        CurrentWeatherResponse morning = service.assemble("서울", List.of(), null, gh,
                LocalDate.of(2026, 7, 20), LocalTime.of(4, 0));
        assertThat(morning.goldenHour()).isEqualTo("04:45");

        // 14:00 → 오전 지남 → 저녁(19:12)
        CurrentWeatherResponse evening = service.assemble("서울", List.of(), null, gh,
                LocalDate.of(2026, 7, 20), LocalTime.of(14, 0));
        assertThat(evening.goldenHour()).isEqualTo("19:12");
    }

    @Test
    @DisplayName("예보 없음/미세먼지 없음이면 해당 필드는 null (부분 응답)")
    void partialNulls() {
        CurrentWeatherResponse r = service.assemble("서울", List.of(), null, null,
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
                "서울", forecasts, null, null,
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
                "서울", forecasts, null, null,
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
                "서울", forecasts, null, null,
                LocalDate.of(2026, 7, 20), LocalTime.of(23, 50));

        assertThat(r.weatherStatus()).isEqualTo("맑음");
        assertThat(r.temperature()).isEqualTo(24.0);
    }
}
