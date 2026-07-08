package com.project.picngo.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ExternalApiErrorCode;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.KmaWeatherApiResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeatherClientTest {

    private MockWebServer mockWebServer;
    private WeatherClient weatherClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // MockWebServer의 URL을 BaseUrl로 주입하여 실제 KMA API 대신 가짜 서버로 요청이 가도록 설정
        String mockUrl = mockWebServer.url("/").toString();
        WebClient.Builder webClientBuilder = WebClient.builder();
        
        weatherClient = new WeatherClient(webClientBuilder, "dummy-test-key", mockUrl, mockUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("기상청 단기예보 정상 조회 시 파싱하여 DTO 리스트를 반환한다")
    void 기상청_단기예보조회_성공() throws Exception {
        // given: 기상청 API에서 내려주는 실제 JSON과 유사한 형태의 가짜 응답 데이터 생성
        String mockJsonResponse = """
            {
              "response": {
                "header": {
                  "resultCode": "00",
                  "resultMsg": "NORMAL_SERVICE"
                },
                "body": {
                  "dataType": "JSON",
                  "items": {
                    "item": [
                      {"fcstDate": "20260702", "fcstTime": "1200", "category": "PTY", "fcstValue": "0"},
                      {"fcstDate": "20260702", "fcstTime": "1200", "category": "SKY", "fcstValue": "1"},
                      {"fcstDate": "20260702", "fcstTime": "1200", "category": "TMP", "fcstValue": "25.5"}
                    ]
                  },
                  "pageNo": 1,
                  "numOfRows": 100,
                  "totalCount": 3
                }
              }
            }
        """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(mockJsonResponse));

        // when
        List<WeatherForecastResponse> forecasts = weatherClient.getForecast(37.5665, 126.9780, "20260702");

        // then
        assertThat(forecasts).hasSize(1);
        WeatherForecastResponse forecast = forecasts.get(0);
        assertThat(forecast.date()).isEqualTo("20260702");
        assertThat(forecast.time()).isEqualTo("1200");
        assertThat(forecast.weatherStatus()).isEqualTo("CLEAR"); // PTY=0, SKY=1 이므로 CLEAR
        assertThat(forecast.temperature()).isEqualTo(25.5);
    }

    @Test
    @DisplayName("기상청 단기예보 API 호출 중 서버 에러가 발생하면 CustomException을 던진다")
    void 기상청_단기예보조회_API에러시_예외발생() {
        // given: 서버 에러 500 응답 설정
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        // when & then
        assertThatThrownBy(() -> weatherClient.getForecast(37.5665, 126.9780, "20260702"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ExternalApiErrorCode.WEATHER_API_ERROR);
    }

    @Test
    @DisplayName("일출일몰 API 정상 조회 시 골든아워 DTO를 반환한다")
    void 일출일몰조회_성공() {
        // given: Sunrise API 전용 가짜 JSON 응답
        String mockSunriseResponse = """
            {
              "results": {
                "sunrise": "10:30:00 PM",
                "sunset": "08:45:00 AM"
              },
              "status": "OK"
            }
        """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(mockSunriseResponse));

        // when
        GoldenHourResponse goldenHour = weatherClient.getGoldenHour(37.56, 126.97, "2026-07-02");

        // then
        assertThat(goldenHour.sunriseTime()).isEqualTo("10:30:00 PM");
        assertThat(goldenHour.sunsetTime()).isEqualTo("08:45:00 AM");
    }
}
