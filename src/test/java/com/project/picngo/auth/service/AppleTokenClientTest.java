package com.project.picngo.auth.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Apple 개발자 키는 Apple Developer Program 승인 후에만 받을 수 있다. 여기서는 같은 형식의
 * EC 키를 만들어 Apple 역할을 MockWebServer로 대신하고, 장애·미설정 때 로그인과 탈퇴가
 * 막히지 않는지를 본다 — 폐기는 못 해도 되지만 그것 때문에 사용자가 막히면 안 된다.
 */
class AppleTokenClientTest {

    private static final String CLIENT_ID = "com.picngo.app";
    private static final String TEAM_ID = "TEAM123456";
    private static final String KEY_ID = "KEY1234567";

    private MockWebServer server;
    private String privateKeyPem;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        privateKeyPem = Base64.getEncoder().encodeToString(ecKeyPair().getPrivate().getEncoded());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("개발자 키가 없으면 교환을 건너뛰고 Apple을 호출하지 않는다")
    void skipsExchangeWhenKeyIsNotConfigured() {
        AppleTokenClient client = client("", "", "");

        assertThat(client.isEnabled()).isFalse();
        assertThat(client.exchangeRefreshToken("code-1")).isNull();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("개발자 키가 없으면 폐기도 건너뛴다 — 승인 전에도 탈퇴가 그대로 동작해야 한다")
    void skipsRevokeWhenKeyIsNotConfigured() {
        AppleTokenClient client = client("", "", "");

        client.revoke("refresh-token-1");

        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("authorizationCode를 refresh token으로 교환하고 client secret을 함께 보낸다")
    void exchangesAuthorizationCode() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"refresh_token\":\"apple-refresh-1\",\"access_token\":\"a\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        String refreshToken = client(TEAM_ID, KEY_ID, privateKeyPem).exchangeRefreshToken("code-1");

        assertThat(refreshToken).isEqualTo("apple-refresh-1");
        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("grant_type=authorization_code").contains("code=code-1");
        // client secret이 비어 있으면 Apple이 401을 준다 — 서명까지 올라갔는지 확인한다.
        assertThat(body).contains("client_secret=ey");
    }

    @Test
    @DisplayName("교환이 실패해도 null을 돌려줄 뿐 예외를 던지지 않는다 — 로그인을 막지 않는다")
    void returnsNullWhenExchangeFails() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid_grant\"}"));

        assertThat(client(TEAM_ID, KEY_ID, privateKeyPem).exchangeRefreshToken("code-1")).isNull();
    }

    @Test
    @DisplayName("응답에 refresh token이 없으면 null로 넘긴다")
    void returnsNullWhenResponseHasNoRefreshToken() {
        server.enqueue(new MockResponse()
                .setBody("{\"access_token\":\"a\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        assertThat(client(TEAM_ID, KEY_ID, privateKeyPem).exchangeRefreshToken("code-1")).isNull();
    }

    @Test
    @DisplayName("폐기 요청에 token_type_hint를 붙여 보낸다")
    void revokesRefreshToken() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        client(TEAM_ID, KEY_ID, privateKeyPem).revoke("apple-refresh-1");

        String body = server.takeRequest().getBody().readUtf8();
        assertThat(body).contains("token=apple-refresh-1").contains("token_type_hint=refresh_token");
    }

    @Test
    @DisplayName("폐기가 실패해도 예외를 던지지 않는다 — Apple 장애가 탈퇴를 막으면 안 된다")
    void swallowsRevokeFailure() {
        server.enqueue(new MockResponse().setResponseCode(503));

        // 예외가 밖으로 나가면 카카오·일반 계정 탈퇴까지 같이 실패한다.
        client(TEAM_ID, KEY_ID, privateKeyPem).revoke("apple-refresh-1");
    }

    @Test
    @DisplayName("authorizationCode가 없으면 교환하지 않는다")
    void skipsExchangeWithoutAuthorizationCode() {
        AppleTokenClient client = client(TEAM_ID, KEY_ID, privateKeyPem);

        assertThat(client.exchangeRefreshToken(null)).isNull();
        assertThat(client.exchangeRefreshToken("  ")).isNull();
        assertThat(server.getRequestCount()).isZero();
    }

    private AppleTokenClient client(String teamId, String keyId, String privateKey) {
        Clock clock = new Clock() {
            @Override public Instant instant() { return Instant.parse("2026-01-01T00:00:00Z"); }
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        };
        return new AppleTokenClient(
                RestClient.builder(),
                server.url("/auth/token").toString(),
                server.url("/auth/revoke").toString(),
                CLIENT_ID, teamId, keyId, privateKey, clock);
    }

    private KeyPair ecKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }
}
