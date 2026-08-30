package com.project.picngo.external;

import com.project.picngo.external.config.ExternalApiCircuitBreakerConfig;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoAddressClientTest {

    private MockWebServer server;
    private KakaoAddressClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new KakaoAddressClient(
                WebClient.builder(),
                new ExternalApiCircuitBreakerConfig().externalApiCircuitBreakerRegistry(),
                "dummy-key",
                server.url("/").toString()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("도로명 주소를 우선 반환하고 카카오 좌표 변환 요청 형식을 지킨다")
    void returnsRoadAddressFirst() throws InterruptedException {
        server.enqueue(jsonResponse("""
                {
                  "documents": [{
                    "road_address": {"address_name": "부산광역시 수영구 광안해변로 219"},
                    "address": {"address_name": "부산광역시 수영구 광안동 192-20"}
                  }]
                }
                """));

        String address = client.coord2Address(35.153386, 129.118785);

        assertThat(address).isEqualTo("부산광역시 수영구 광안해변로 219");

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("KakaoAK dummy-key");
        assertThat(request.getRequestUrl().encodedPath())
                .isEqualTo("/v2/local/geo/coord2address.json");
        assertThat(request.getRequestUrl().queryParameter("x")).isEqualTo("129.118785");
        assertThat(request.getRequestUrl().queryParameter("y")).isEqualTo("35.153386");
        assertThat(request.getRequestUrl().queryParameter("input_coord")).isEqualTo("WGS84");
    }

    @Test
    @DisplayName("도로명 주소가 없으면 지번 주소를 반환한다")
    void fallsBackToLotNumberAddress() {
        server.enqueue(jsonResponse("""
                {
                  "documents": [{
                    "road_address": null,
                    "address": {"address_name": "부산광역시 수영구 광안동 192-20"}
                  }]
                }
                """));

        assertThat(client.coord2Address(35.153386, 129.118785))
                .isEqualTo("부산광역시 수영구 광안동 192-20");
    }

    @Test
    @DisplayName("응답 문서가 비어 있으면 null을 반환한다")
    void emptyDocumentsReturnsNull() {
        server.enqueue(jsonResponse("{\"documents\":[]}"));

        assertThat(client.coord2Address(35.153386, 129.118785)).isNull();
    }

    @Test
    @DisplayName("좌표가 유효하지 않으면 HTTP 요청 없이 null을 반환한다")
    void invalidCoordinateReturnsNullWithoutRequest() {
        assertThat(client.coord2Address(null, 129.118785)).isNull();
        assertThat(client.coord2Address(91.0, 129.118785)).isNull();
        assertThat(client.coord2Address(35.153386, Double.NaN)).isNull();
        assertThat(client.coord2Address(0.0, 0.0)).isNull();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("카카오 API 오류가 발생하면 null을 반환한다")
    void apiErrorReturnsNull() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThat(client.coord2Address(35.153386, 129.118785)).isNull();
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setBody(body)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }
}
