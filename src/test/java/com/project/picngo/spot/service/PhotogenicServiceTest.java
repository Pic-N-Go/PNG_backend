package com.project.picngo.spot.service;

import com.project.picngo.external.dto.AirQualityResponse.Item;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.external.service.WeatherCacheService;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.dto.PhotogenicResponse;
import com.project.picngo.spot.repository.SeasonEventRepository;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PhotogenicServiceTest {

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private SeasonEventRepository seasonEventRepository;

    @Mock
    private WeatherCacheService weatherCacheService;

    @InjectMocks
    private PhotogenicService photogenicService;

    @Test
    @DisplayName("충청남도 주소인 경우 에어코리아 지역명을 '충남'으로 정규화하여 캐시를 조회한다")
    void calculate_normalizesChungnamToChungnam() {
        // given
        Long spotId = 1L;
        LocalDate date = LocalDate.of(2026, 9, 1);
        LocalTime time = LocalTime.of(14, 0);

        Spot spot = Spot.builder()
                .name("공산성")
                .address("충청남도 공주시 웅진로 280")
                .latitude(36.460)
                .longitude(127.126)
                .cat3("A02010100")
                .build();

        given(spotRepository.findById(spotId)).willReturn(Optional.of(spot));
        given(seasonEventRepository.findActiveByRegion("충남")).willReturn(Collections.emptyList());

        // 미세먼지 좋음(1등급), 오존 좋음(1등급)
        Item airItem = new Item("공주시", "15", "10", "0.020", "1", "1");
        given(weatherCacheService.getCachedAirQuality("충남")).willReturn(airItem);

        // 맑음(CLEAR) 예보
        List<WeatherForecastResponse> forecasts = List.of(
                new WeatherForecastResponse("20260901", "1400", "CLEAR", 25.0)
        );
        given(weatherCacheService.getCached7DayForecast(eq(36.460), eq(127.126), eq("20260901")))
                .willReturn(forecasts);

        // 골든아워 (14:00은 일출 06:00 / 일몰 19:00 사이이므로 비해당)
        GoldenHourResponse goldenHour = new GoldenHourResponse(
                "2026-09-01T06:00:00+09:00",
                "2026-09-01T19:00:00+09:00",
                "05:30", "18:30"
        );
        given(weatherCacheService.getCachedGoldenHour(eq(36.460), eq(127.126), eq("2026-09-01")))
                .willReturn(goldenHour);

        // when
        PhotogenicResponse response = photogenicService.calculate(spotId, date, time);

        // then
        verify(weatherCacheService).getCachedAirQuality("충남");
        assertThat(response).isNotNull();
        assertThat(response.weather().score()).isEqualTo(30); // 맑음 30점
        assertThat(response.fineDust().score()).isEqualTo(20); // 미세먼지 좋음 20점
        assertThat(response.ozone().score()).isEqualTo(10); // 오존 좋음 10점
        assertThat(response.score()).isGreaterThanOrEqualTo(60);
    }
}
