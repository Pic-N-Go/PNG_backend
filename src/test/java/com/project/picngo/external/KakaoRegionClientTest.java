package com.project.picngo.external;

import com.project.picngo.external.config.ExternalApiCircuitBreakerConfig;
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

import static org.assertj.core.api.Assertions.assertThat;

class KakaoRegionClientTest {

    private MockWebServer server;
    private KakaoRegionClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        // 서킷 상태가 테스트 간에 새지 않도록 매 테스트마다 새 레지스트리를 만든다.
        client = new KakaoRegionClient(
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
    @DisplayName("좌표로 시/도 이름을 반환한다")
    void returnsRegion() {
        String body = """
                {"documents":[{"region_1depth_name":"서울특별시","region_2depth_name":"중구"}]}
                """;
        server.enqueue(new MockResponse()
                .setBody(body)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        String region = client.coord2region(37.56, 126.97);

        assertThat(region).isEqualTo("서울특별시");
    }

    @Test
    @DisplayName("문서가 비면 null")
    void emptyDocumentsReturnsNull() {
        server.enqueue(new MockResponse()
                .setBody("{\"documents\":[]}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client.coord2region(0.0, 0.0)).isNull();
    }

    @Test
    @DisplayName("API 오류면 null (예외 삼킴)")
    void errorReturnsNull() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThat(client.coord2region(37.56, 126.97)).isNull();
    }
}
