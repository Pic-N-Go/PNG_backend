package com.project.picngo.external.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.picngo.external.WeatherClient;
import com.project.picngo.external.dto.KmaMidWeatherApiResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WeatherForecastServiceTest {

    @Mock
    private WeatherClient weatherClient;

    @InjectMocks
    private WeatherForecastService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("시간대별 중기예보 최신 발표시각(tmFc)을 정확하게 산출한다")
    void calculateLatestMidTermTmFc() {
        // 06:30 이전 -> 전일 18시
        LocalDateTime morningEarly = LocalDateTime.of(2026, 9, 20, 5, 0);
        assertThat(service.calculateLatestMidTermTmFc(morningEarly)).isEqualTo("202609191800");

        LocalDateTime boundaryBefore0630 = LocalDateTime.of(2026, 9, 20, 6, 29);
        assertThat(service.calculateLatestMidTermTmFc(boundaryBefore0630)).isEqualTo("202609191800");

        // 06:30 ~ 18:30 -> 당일 06시
        LocalDateTime boundary0630 = LocalDateTime.of(2026, 9, 20, 6, 30);
        assertThat(service.calculateLatestMidTermTmFc(boundary0630)).isEqualTo("202609200600");

        LocalDateTime noon = LocalDateTime.of(2026, 9, 20, 13, 0);
        assertThat(service.calculateLatestMidTermTmFc(noon)).isEqualTo("202609200600");

        LocalDateTime boundaryBefore1830 = LocalDateTime.of(2026, 9, 20, 18, 29);
        assertThat(service.calculateLatestMidTermTmFc(boundaryBefore1830)).isEqualTo("202609200600");

        // 18:30 이후 -> 당일 18시
        LocalDateTime boundary1830 = LocalDateTime.of(2026, 9, 20, 18, 30);
        assertThat(service.calculateLatestMidTermTmFc(boundary1830)).isEqualTo("202609201800");

        LocalDateTime night = LocalDateTime.of(2026, 9, 20, 23, 50);
        assertThat(service.calculateLatestMidTermTmFc(night)).isEqualTo("202609201800");
    }

    @Test
    @DisplayName("위경도 좌표를 기상청 중기육상예보 구역코드(regId)로 정확하게 매핑한다")
    void getMidTermRegionCode() {
        // 서울시청
        assertThat(service.getMidTermRegionCode(37.5665, 126.9780)).isEqualTo("11B00000");

        // 강원 영동 (속초, 강릉)
        assertThat(service.getMidTermRegionCode(38.2070, 128.5918)).isEqualTo("11D20000"); // 속초
        assertThat(service.getMidTermRegionCode(37.7518, 128.8760)).isEqualTo("11D20000"); // 강릉

        // 강원 영서 (춘천, 원주)
        assertThat(service.getMidTermRegionCode(37.8813, 127.7298)).isEqualTo("11D10000"); // 춘천
        assertThat(service.getMidTermRegionCode(37.3422, 127.9201)).isEqualTo("11D10000"); // 원주

        // 충북 (청주) & 충남/대전/세종 (대전)
        assertThat(service.getMidTermRegionCode(36.6424, 127.4890)).isEqualTo("11C10000"); // 청주
        assertThat(service.getMidTermRegionCode(36.3504, 127.3845)).isEqualTo("11C10000"); // 대전(lng 127.38 -> 충북권 바운더리)
        assertThat(service.getMidTermRegionCode(36.6588, 126.6728)).isEqualTo("11C20000"); // 홍성/충남 서부

        // 호남: 전북 (전주), 전남/광주 (광주)
        assertThat(service.getMidTermRegionCode(35.8242, 127.1480)).isEqualTo("11F10000"); // 전주
        assertThat(service.getMidTermRegionCode(35.1595, 126.8526)).isEqualTo("11F20000"); // 광주

        // 영남: 경북 (안동, 대구), 경남 (부산)
        assertThat(service.getMidTermRegionCode(35.8714, 128.6014)).isEqualTo("11H10000"); // 대구
        assertThat(service.getMidTermRegionCode(35.1796, 129.0756)).isEqualTo("11H20000"); // 부산

        // 제주도
        assertThat(service.getMidTermRegionCode(33.4996, 126.5312)).isEqualTo("11G00000");
    }

    @Test
    @DisplayName("기상청 중기예보 빈 문자열 items 파싱 시 역직렬화 에러 없이 빈 목록으로 처리된다")
    void deserializeEmptyItemsSafely() throws Exception {
        String jsonWithEmptyItemsString = """
            {
              "response": {
                "header": {
                  "resultCode": "03",
                  "resultMsg": "NO_DATA"
                },
                "body": {
                  "dataType": "JSON",
                  "items": "",
                  "pageNo": 1,
                  "numOfRows": 10,
                  "totalCount": 0
                }
              }
            }
            """;

        KmaMidWeatherApiResponse response = objectMapper.readValue(jsonWithEmptyItemsString, KmaMidWeatherApiResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.response().header().resultCode()).isEqualTo("03");
        assertThat(response.response().body().items().item()).isEmpty();
    }

    @Test
    @DisplayName("단기예보와 중기예보가 병합될 때 중복 날짜 없이 정상 결합된다")
    void getCombined7DayForecastMergesWithoutOverlap() {
        // given
        // 단기예보는 20260920, 20260921, 20260922 3일치 제공
        List<WeatherForecastResponse> shortTerms = List.of(
                new WeatherForecastResponse("20260920", "1200", "CLEAR", 22.0),
                new WeatherForecastResponse("20260921", "1200", "CLOUDY", 21.0),
                new WeatherForecastResponse("20260922", "1200", "RAINY", 19.0)
        );
        given(weatherClient.getShortTermForecast(anyDouble(), anyDouble(), anyString()))
                .willReturn(shortTerms);

        // 중기예보 목업 응답
        KmaMidWeatherApiResponse.Item midItem = new KmaMidWeatherApiResponse.Item(
                "11D20000",
                null, null, null, null, null, null, null, null, null, null,
                null, null, null,
                "맑음", "맑음",  // Day 3 (20260923)
                "구름많음", "구름많음", // Day 4 (20260924)
                "흐림", "흐림",  // Day 5 (20260925)
                "비", "비",    // Day 6 (20260926)
                "맑음", "맑음",  // Day 7 (20260927)
                null, null, null
        );
        KmaMidWeatherApiResponse midResponse = new KmaMidWeatherApiResponse(
                new KmaMidWeatherApiResponse.Response(
                        new KmaMidWeatherApiResponse.Header("00", "NORMAL_SERVICE"),
                        new KmaMidWeatherApiResponse.Body("JSON", new KmaMidWeatherApiResponse.Items(List.of(midItem)), 1, 10, 1)
                )
        );
        given(weatherClient.getMidTermForecast(anyString(), anyString()))
                .willReturn(midResponse);

        // when
        List<WeatherForecastResponse> result = service.getCombined7DayForecast(38.20, 128.59, "20260920");

        // then
        assertThat(result).isNotEmpty();
        // 단기 3개 + 중기 5일치(각 날짜별 1000, 1400, 1800 총 3슬롯 = 15개)
        assertThat(result.size()).isEqualTo(3 + 15);
        // 단기예보 날짜인 20260920, 20260921, 20260922에는 중기예보가 끼어들지 않음
        long countDay20 = result.stream().filter(r -> "20260920".equals(r.date())).count();
        assertThat(countDay20).isEqualTo(1); // 단기예보 1개만 존재
    }

    @Test
    @DisplayName("D+3일차에 단기예보가 1800만 갖고 있을 때 누락된 1000과 1400 슬롯을 중기예보가 보완한다")
    void getCombined7DayForecastSupplementsMissingSlotsOnDay3() {
        // given: D+3(20260923)에 1800 단기예보만 있는 상황
        List<WeatherForecastResponse> shortTerms = List.of(
                new WeatherForecastResponse("20260920", "1200", "CLEAR", 22.0),
                new WeatherForecastResponse("20260923", "1800", "CLEAR", 20.0) // D+3 저녁만 존재
        );
        given(weatherClient.getShortTermForecast(anyDouble(), anyDouble(), anyString()))
                .willReturn(shortTerms);

        KmaMidWeatherApiResponse.Item midItem = new KmaMidWeatherApiResponse.Item(
                "11D20000",
                null, null, null, null, null, null, null, null, null, null,
                null, null, null,
                "맑음", "구름많음", // Day 3 (20260923) - 1000=맑음, 1400/1800=구름많음
                "맑음", "맑음",
                "맑음", "맑음",
                "맑음", "맑음",
                "맑음", "맑음",
                null, null, null
        );
        KmaMidWeatherApiResponse midResponse = new KmaMidWeatherApiResponse(
                new KmaMidWeatherApiResponse.Response(
                        new KmaMidWeatherApiResponse.Header("00", "NORMAL_SERVICE"),
                        new KmaMidWeatherApiResponse.Body("JSON", new KmaMidWeatherApiResponse.Items(List.of(midItem)), 1, 10, 1)
                )
        );
        given(weatherClient.getMidTermForecast(anyString(), anyString()))
                .willReturn(midResponse);

        // when
        List<WeatherForecastResponse> result = service.getCombined7DayForecast(38.20, 128.59, "20260920");

        // then: D+3(20260923) 날짜에 대해 1000(중기), 1400(중기), 1800(단기) 3슬롯이 모두 존재해야 함
        List<WeatherForecastResponse> day3Slots = result.stream()
                .filter(r -> "20260923".equals(r.date()))
                .toList();

        assertThat(day3Slots).hasSize(3);

        WeatherForecastResponse slot1000 = day3Slots.stream().filter(r -> "1000".equals(r.time())).findFirst().orElse(null);
        WeatherForecastResponse slot1400 = day3Slots.stream().filter(r -> "1400".equals(r.time())).findFirst().orElse(null);
        WeatherForecastResponse slot1800 = day3Slots.stream().filter(r -> "1800".equals(r.time())).findFirst().orElse(null);

        assertThat(slot1000).isNotNull();
        assertThat(slot1000.weatherStatus()).isEqualTo("CLEAR");

        assertThat(slot1400).isNotNull();
        assertThat(slot1400.weatherStatus()).isEqualTo("CLOUDY");

        assertThat(slot1800).isNotNull();
        assertThat(slot1800.temperature()).isEqualTo(20.0); // 단기예보의 1800 슬롯이 보존됨
    }
}
