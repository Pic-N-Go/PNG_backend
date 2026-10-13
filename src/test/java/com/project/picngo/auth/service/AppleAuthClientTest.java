package com.project.picngo.auth.service;

import com.project.picngo.auth.dto.AppleProfile;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AuthErrorCode;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 실제 Apple 토큰은 실기기에서만 받을 수 있다. 여기서는 테스트용 RSA 키로 Apple 역할을 대신해
 * (공개 키는 MockWebServer가 JWKS로 내려준다) 검증 규칙이 지켜지는지를 본다.
 */
class AppleAuthClientTest {

    private static final String CLIENT_ID = "com.picngo.app";
    private static final String KEY_ID = "test-key";

    private MockWebServer server;
    private AppleAuthClient client;
    private KeyPair appleKey;
    private Instant now;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        appleKey = rsaKeyPair();
        now = Instant.now();
        // 시계를 테스트가 쥐고 있어야 키 재조회 간격을 기다리지 않고 넘길 수 있다.
        Clock clock = new Clock() {
            @Override public Instant instant() { return now; }
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        };
        client = new AppleAuthClient(RestClient.builder(), server.url("/auth/keys").toString(), CLIENT_ID, clock);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("Apple이 서명한 토큰이면 sub와 email을 꺼낸다")
    void returnsProfileFromValidToken() {
        enqueueJwks(appleKey);

        AppleProfile profile = client.getProfile(token().signWith(appleKey.getPrivate()).compact());

        assertThat(profile.providerId()).isEqualTo("001234.abcdef.5678");
        assertThat(profile.email()).isEqualTo("user@privaterelay.appleid.com");
    }

    @Test
    @DisplayName("email 클레임이 없으면 sub 기반 대체 이메일을 쓴다")
    void fallsBackWhenEmailIsMissing() {
        enqueueJwks(appleKey);

        AppleProfile profile = client.getProfile(
                token().claim("email", null).signWith(appleKey.getPrivate()).compact());

        assertThat(profile.email()).isEqualTo("001234.abcdef.5678@apple.local");
    }

    /** 이 검사가 빠지면 다른 앱에서 받은 Apple 토큰으로 우리 서비스에 로그인할 수 있다. */
    @Test
    @DisplayName("다른 앱(aud)용으로 발급된 토큰은 거부한다")
    void rejectsTokenForAnotherApp() {
        enqueueJwks(appleKey);

        assertInvalid(token().claim("aud", "com.other.app").signWith(appleKey.getPrivate()).compact());
    }

    @Test
    @DisplayName("Apple 키가 아닌 키로 서명한 토큰은 거부한다")
    void rejectsTokenSignedByAnotherKey() throws Exception {
        enqueueJwks(appleKey);

        assertInvalid(token().signWith(rsaKeyPair().getPrivate()).compact());
    }

    @Test
    @DisplayName("만료된 토큰은 거부한다")
    void rejectsExpiredToken() {
        enqueueJwks(appleKey);

        assertInvalid(token()
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(appleKey.getPrivate())
                .compact());
    }

    @Test
    @DisplayName("발급자가 Apple이 아닌 토큰은 거부한다")
    void rejectsTokenFromAnotherIssuer() {
        enqueueJwks(appleKey);

        assertInvalid(token().issuer("https://evil.example.com").signWith(appleKey.getPrivate()).compact());
    }

    @Test
    @DisplayName("JWKS에 없는 kid의 토큰은 거부한다")
    void rejectsUnknownKeyId() {
        enqueueJwks(appleKey);

        assertInvalid(token().header().keyId("unknown").and().signWith(appleKey.getPrivate()).compact());
    }

    @Test
    @DisplayName("서명 없는 토큰이나 JWT가 아닌 값은 거부한다")
    void rejectsUnsignedOrMalformedToken() {
        assertInvalid(token().compact());
        assertInvalid("not-a-jwt");
    }

    @Test
    @DisplayName("만료(exp)가 없는 토큰은 거부한다")
    void rejectsTokenWithoutExpiration() {
        enqueueJwks(appleKey);

        assertInvalid(token().expiration(null).signWith(appleKey.getPrivate()).compact());
    }

    /** 첫 키 조회 전에 kid 없는 토큰이 오면 NPE(500)가 아니라 401이어야 한다. */
    @Test
    @DisplayName("kid가 없는 토큰은 거부한다")
    void rejectsTokenWithoutKeyId() {
        assertInvalid(Jwts.builder()
                .issuer("https://appleid.apple.com")
                .claim("aud", CLIENT_ID)
                .subject("001234.abcdef.5678")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(appleKey.getPrivate())
                .compact());
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("Apple 공개 키를 못 받아오면 502로 구분해 알리고, 장애 중에는 요청마다 다시 호출하지 않는다")
    void reportsKeyFetchFailureWithoutHammeringApple() {
        server.enqueue(new MockResponse().setResponseCode(503));
        String token = token().signWith(appleKey.getPrivate()).compact();

        for (int i = 0; i < 5; i++) {
            assertKeyFetchFailed(token);
        }

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("JWKS가 아닌 응답은 토큰 오류가 아니라 키 조회 실패로 알린다")
    void reportsMalformedJwksAsKeyFetchFailure() {
        server.enqueue(new MockResponse()
                .setBody("<html>maintenance</html>")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE));

        assertKeyFetchFailed(token().signWith(appleKey.getPrivate()).compact());
    }

    @Test
    @DisplayName("Apple이 키를 교체하면 재조회 간격이 지난 뒤 새 키로 서명된 토큰을 받는다")
    void acceptsRotatedKeyAfterRefetchInterval() throws Exception {
        enqueueJwks(appleKey);
        client.getProfile(token().signWith(appleKey.getPrivate()).compact());

        KeyPair rotated = rsaKeyPair();
        String newToken = token().header().keyId("rotated").and().signWith(rotated.getPrivate()).compact();

        // 간격 안에서는 다시 받아오지 않는다 — 조작된 kid로 외부 호출을 유발할 수 없다.
        assertInvalid(newToken);
        assertThat(server.getRequestCount()).isEqualTo(1);

        now = now.plus(Duration.ofMinutes(2));
        enqueueJwks(rotated, "rotated");

        assertThat(client.getProfile(newToken).providerId()).isEqualTo("001234.abcdef.5678");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("동시에 첫 로그인이 몰려도 공개 키는 한 번만 받아온다")
    void fetchesKeysOnceUnderConcurrentFirstLogins() throws Exception {
        enqueueJwks(appleKey);
        String token = token().signWith(appleKey.getPrivate()).compact();

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(10);
        try {
            java.util.List<java.util.concurrent.Future<AppleProfile>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) {
                results.add(pool.submit(() -> client.getProfile(token)));
            }
            for (java.util.concurrent.Future<AppleProfile> result : results) {
                assertThat(result.get().providerId()).isEqualTo("001234.abcdef.5678");
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("공개 키는 캐시해 로그인마다 Apple을 호출하지 않는다")
    void cachesKeys() {
        enqueueJwks(appleKey);
        String token = token().signWith(appleKey.getPrivate()).compact();

        client.getProfile(token);
        client.getProfile(token);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    private void assertKeyFetchFailed(String identityToken) {
        CustomException exception = assertThrows(CustomException.class, () -> client.getProfile(identityToken));
        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.APPLE_KEYS_FETCH_FAILED);
    }

    private void assertInvalid(String identityToken) {
        CustomException exception = assertThrows(CustomException.class, () -> client.getProfile(identityToken));
        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.APPLE_IDENTITY_TOKEN_INVALID);
    }

    /** Apple이 실제로 내려주는 모양과 같게 aud를 배열이 아닌 문자열로 넣는다. */
    private JwtBuilder token() {
        return Jwts.builder()
                .header().keyId(KEY_ID).and()
                .issuer("https://appleid.apple.com")
                .claim("aud", CLIENT_ID)
                .subject("001234.abcdef.5678")
                .claim("email", "user@privaterelay.appleid.com")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)));
    }

    private void enqueueJwks(KeyPair keyPair) {
        enqueueJwks(keyPair, KEY_ID);
    }

    private void enqueueJwks(KeyPair keyPair, String keyId) {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        String body = """
                {"keys":[{"kty":"RSA","kid":"%s","use":"sig","alg":"RS256","n":"%s","e":"%s"}]}
                """.formatted(keyId, base64Url(publicKey.getModulus()), base64Url(publicKey.getPublicExponent()));
        server.enqueue(new MockResponse()
                .setBody(body)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
    }

    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        // BigInteger는 부호 비트용 0x00을 앞에 붙일 수 있다. JWK는 부호 없는 값이어야 한다.
        int offset = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(java.util.Arrays.copyOfRange(bytes, offset, bytes.length));
    }

    private static KeyPair rsaKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
