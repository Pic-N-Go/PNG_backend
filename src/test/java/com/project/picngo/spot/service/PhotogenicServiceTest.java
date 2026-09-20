package com.project.picngo.spot.service;

import com.project.picngo.external.dto.AirQualityResponse.Item;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.external.service.WeatherCacheService;
import com.project.picngo.spot.domain.SeasonEvent;
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
import static org.mockito.ArgumentMatchers.any;
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
        assertThat(response.weather().score()).isEqualTo(35); // 맑음 35점
        assertThat(response.fineDust().score()).isEqualTo(25); // 미세먼지 좋음 25점
        assertThat(response.ozone().score()).isEqualTo(10); // 오존 좋음 10점
        // 활성 시즌 이벤트가 없어 비수기 → 0점이 아니라 평상시 baseline(만점 20의 25% = 5점)
        assertThat(response.season().label()).isEqualTo("평상시");
        assertThat(response.season().score()).isEqualTo(5);
        assertThat(response.score()).isEqualTo(75); // 35 + 25 + 10 + 5(평상시) = 75점
    }

    // 벚꽃: 시작 03-15, 피크 03-28~04-10, 종료 04-20, 만점 20 (cat3 제한 없음)
    private SeasonEvent cherryBlossom() {
        return SeasonEvent.builder()
                .name("벚꽃")
                .monthDayStart("03-15")
                .monthDayPeakStart("03-28")
                .monthDayPeakEnd("04-10")
                .monthDayEnd("04-20")
                .region(null)
                .maxScore(20)
                .isActive(true)
                .eligibleCat3(null)
                .build();
    }

    // 날씨/대기질/골든아워는 stub하지 않아 0점 → season 점수만 검증할 수 있도록 격리
    private PhotogenicResponse seasonOn(LocalDate date) {
        Spot spot = Spot.builder()
                .name("석촌호수")
                .address("서울특별시 송파구")
                .latitude(37.5)
                .longitude(127.1)
                .cat3("A02010100")
                .build();
        given(spotRepository.findById(1L)).willReturn(Optional.of(spot));
        given(seasonEventRepository.findActiveByRegion(any())).willReturn(List.of(cherryBlossom()));
        return photogenicService.calculate(1L, date, LocalTime.NOON);
    }

    @Test
    @DisplayName("시즌 피크 구간에서는 만점(100%)을 준다")
    void season_peakGivesFullScore() {
        PhotogenicResponse r = seasonOn(LocalDate.of(2026, 4, 1)); // 피크(03-28~04-10) 내
        assertThat(r.season().score()).isEqualTo(20);
        assertThat(r.season().label()).isEqualTo("벚꽃 100%");
    }

    @Test
    @DisplayName("시즌 시작 경계에서는 baseline(25%)로 수렴한다")
    void season_rangeStartConvergesToBaseline() {
        PhotogenicResponse r = seasonOn(LocalDate.of(2026, 3, 15)); // 시작일 = 램프 하단
        assertThat(r.season().score()).isEqualTo(5); // 20 * 0.25
        assertThat(r.season().label()).isEqualTo("벚꽃 25%");
    }

    @Test
    @DisplayName("시작~피크 사이는 선형으로 보간된다")
    void season_rampIsLinear() {
        // 03-15 ~ 03-28(13일 램프) 중 03-21은 6일차 → 5 + (20-5)*(6/13) = 11.9 -> 12
        PhotogenicResponse r = seasonOn(LocalDate.of(2026, 3, 21));
        assertThat(r.season().score()).isEqualTo(12);
        assertThat(r.season().label()).isEqualTo("벚꽃 60%");
    }

    @Test
    @DisplayName("시즌 기간 밖(비수기)에는 0점이 아니라 평상시 baseline을 준다")
    void season_offSeasonGivesBaseline() {
        PhotogenicResponse r = seasonOn(LocalDate.of(2026, 6, 1)); // 벚꽃 종료(04-20) 이후, 여름 시작 전
        assertThat(r.season().score()).isEqualTo(5);
        assertThat(r.season().label()).isEqualTo("평상시");
    }
}
