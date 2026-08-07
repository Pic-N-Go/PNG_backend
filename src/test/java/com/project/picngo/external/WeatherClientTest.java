package com.project.picngo.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ExternalApiErrorCode;
import com.project.picngo.external.config.ExternalApiCircuitBreakerConfig;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.KmaWeatherApiResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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

        // 생성자 순서: (builder, circuitBreakerRegistry, kmaUrl, sunriseUrl, serviceKey)
        // 서킷 상태가 테스트 간에 새지 않도록 매 테스트마다 새 레지스트리를 만든다.
        weatherClient = new WeatherClient(
                webClientBuilder,
                new ExternalApiCircuitBreakerConfig().externalApiCircuitBreakerRegistry(),
                mockUrl, mockUrl, "dummy-test-key");
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
        List<WeatherForecastResponse> forecasts = weatherClient.getShortTermForecast(37.5665, 126.9780, "20260702");

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
        // given: 항상 500을 반환 (프로덕션 재시도로 여러 번 호출되므로 단일 enqueue 대신 dispatcher 사용)
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(500).setBody("Internal Server Error");
            }
        });

        // when & then
        assertThatThrownBy(() -> weatherClient.getShortTermForecast(37.5665, 126.9780, "20260702"))
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

    @Test
    @DisplayName("기상청 API가 연속 실패하면 서킷이 열려 더 이상 원격 호출을 시도하지 않는다")
    void 기상청_연속실패시_서킷오픈() {
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(500).setBody("Internal Server Error");
            }
        });

        // 판단에 필요한 최소 표본(5건)을 채워 서킷을 연다
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> weatherClient.getShortTermForecast(37.5665, 126.9780, "20260702"))
                    .isInstanceOf(CustomException.class);
        }
        int requestsWhenOpened = mockWebServer.getRequestCount();

        // 서킷이 열린 뒤에는 호출을 시도조차 하지 않아야 한다.
        // 이게 보장돼야 외부 API 장애 시 스레드가 대기에 묶이지 않는다.
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> weatherClient.getShortTermForecast(37.5665, 126.9780, "20260702"))
                    .isInstanceOf(CustomException.class);
        }

        assertThat(mockWebServer.getRequestCount())
                .as("서킷이 열린 뒤에는 원격 요청이 추가로 나가면 안 된다")
                .isEqualTo(requestsWhenOpened);
    }

    @Test
    @DisplayName("기상청 서킷이 열려도 일출일몰 API는 별개 서킷이라 정상 동작한다")
    void 기상청서킷_오픈시_일출일몰은_영향없음() {
        String sunriseBody = """
            {
              "results": {"sunrise": "10:30:00 PM", "sunset": "08:45:00 AM"},
              "status": "OK"
            }
        """;

        // 같은 목 서버를 쓰므로 경로로 갈라, 기상청만 죽은 상황을 만든다
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.contains("/json")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .setBody(sunriseBody);
                }
                return new MockResponse().setResponseCode(500).setBody("Internal Server Error");
            }
        });

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> weatherClient.getShortTermForecast(37.5665, 126.9780, "20260702"))
                    .isInstanceOf(CustomException.class);
        }

        // 서킷을 하나로 합쳤다면 여기서 같이 막혀버린다.
        // 호스트별로 나눴기 때문에 일출일몰은 멀쩡히 동작해야 한다.
        GoldenHourResponse goldenHour = weatherClient.getGoldenHour(37.56, 126.97, "2026-07-02");
        assertThat(goldenHour.sunriseTime()).isEqualTo("10:30:00 PM");
    }
}
