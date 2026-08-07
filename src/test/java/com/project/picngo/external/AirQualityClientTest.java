package com.project.picngo.external;

import com.project.picngo.external.config.ExternalApiCircuitBreakerConfig;
import com.project.picngo.external.dto.AirQualityResponse.Item;
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

import static org.assertj.core.api.Assertions.assertThat;

class AirQualityClientTest {

    private MockWebServer server;
    private AirQualityClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        // 서킷 상태가 테스트 간에 새지 않도록 매 테스트마다 새 레지스트리를 만든다.
        client = new AirQualityClient(
                WebClient.builder(),
                new ExternalApiCircuitBreakerConfig().externalApiCircuitBreakerRegistry(),
                "dummy-key",
                server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("측정값이 있는 첫 관측소를 반환한다")
    void returnsFirstMeasuredItem() {
        // 첫 항목은 pm10Value가 "-"라 걸러지고 두 번째가 선택돼야 한다
        String body = """
                {"response":{"body":{"items":[
                  {"stationName":"점검중","pm10Value":"-","pm25Value":"-"},
                  {"stationName":"중구","pm10Value":"35","pm25Value":"18","pm10Grade":"2"}
                ]}}}
                """;
        server.enqueue(new MockResponse()
                .setBody(body)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        Item item = client.getAirQuality("서울");

        assertThat(item).isNotNull();
        assertThat(item.stationName()).isEqualTo("중구");
        assertThat(item.pm10Value()).isEqualTo("35");
    }

    @Test
    @DisplayName("API 오류면 null (예외 삼킴)")
    void errorReturnsNull() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThat(client.getAirQuality("서울")).isNull();
    }

    @Test
    @DisplayName("연속 실패하면 서킷이 열려 더 이상 원격 호출을 시도하지 않는다")
    void opensCircuitAfterRepeatedFailures() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(500);
            }
        });

        // 판단에 필요한 최소 표본(5건)을 채워 서킷을 연다
        for (int i = 0; i < 5; i++) {
            assertThat(client.getAirQuality("서울")).isNull();
        }
        int requestsWhenOpened = server.getRequestCount();

        // 서킷이 열린 뒤에는 호출을 시도조차 하지 않아야 한다.
        // 이게 보장돼야 외부 API 장애 시 스레드가 대기에 묶이지 않는다.
        for (int i = 0; i < 5; i++) {
            assertThat(client.getAirQuality("서울")).isNull();
        }

        assertThat(server.getRequestCount())
                .as("서킷이 열린 뒤에는 원격 요청이 추가로 나가면 안 된다")
                .isEqualTo(requestsWhenOpened);
    }
}
