package com.project.picngo.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

/**
 * Apple 토큰 교환·폐기. identity token 검증(AppleAuthClient)과 달리 여기서는 우리 쪽 신원을
 * 증명해야 해서, Apple 개발자 키(.p8)로 서명한 client secret이 필요하다.
 *
 * 쓰는 곳은 하나다 — 탈퇴 시 Apple 토큰 폐기(App Store 가이드라인 5.1.1(v)). 로그인 때 받는
 * authorizationCode는 5분이면 만료되므로 그때 refresh token으로 바꿔 두고, 탈퇴할 때 폐기한다.
 *
 * ponytail: 키가 설정되지 않으면 조용히 비활성이다. Apple Developer 승인 전이나 로컬·테스트에서
 * 키 없이도 로그인과 탈퇴가 그대로 동작해야 한다. 켜려면 APPLE_TEAM_ID·APPLE_KEY_ID·
 * APPLE_PRIVATE_KEY를 넣으면 된다.
 */
@Slf4j
@Component
public class AppleTokenClient {

	private static final String AUDIENCE = "https://appleid.apple.com";
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	/** Apple이 허용하는 client secret 최대 수명은 6개월이지만, 매 요청 새로 만들므로 짧게 쓴다. */
	private static final Duration CLIENT_SECRET_TTL = Duration.ofMinutes(5);

	private final RestClient restClient;
	private final String tokenUrl;
	private final String revokeUrl;
	private final String clientId;
	private final String teamId;
	private final String keyId;
	private final String privateKeyPem;
	private final Clock clock;

	@Autowired
	public AppleTokenClient(
		RestClient.Builder restClientBuilder,
		@Value("${apple.auth.token-url}") String tokenUrl,
		@Value("${apple.auth.revoke-url}") String revokeUrl,
		@Value("${apple.auth.client-id}") String clientId,
		@Value("${apple.auth.team-id:}") String teamId,
		@Value("${apple.auth.key-id:}") String keyId,
		@Value("${apple.auth.private-key:}") String privateKeyPem
	) {
		this(restClientBuilder, tokenUrl, revokeUrl, clientId, teamId, keyId, privateKeyPem, Clock.systemUTC());
	}

	AppleTokenClient(
		RestClient.Builder restClientBuilder,
		String tokenUrl,
		String revokeUrl,
		String clientId,
		String teamId,
		String keyId,
		String privateKeyPem,
		Clock clock
	) {
		// AppleAuthClient와 같은 이유 — 타임아웃이 없으면 Apple 응답이 늦을 때 요청 스레드가 묶인다.
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(TIMEOUT);
		requestFactory.setReadTimeout(TIMEOUT);
		this.restClient = restClientBuilder.requestFactory(requestFactory).build();
		this.tokenUrl = tokenUrl;
		this.revokeUrl = revokeUrl;
		this.clientId = clientId;
		this.teamId = teamId;
		this.keyId = keyId;
		this.privateKeyPem = privateKeyPem;
		this.clock = clock;
	}

	/** 키가 설정돼 있는지. 안 돼 있으면 교환·폐기를 건너뛴다. */
	public boolean isEnabled() {
		return !teamId.isBlank() && !keyId.isBlank() && !privateKeyPem.isBlank();
	}

	/**
	 * authorizationCode를 refresh token으로 교환한다. 실패하면 null — 로그인을 막지 않는다.
	 * 토큰을 못 받으면 나중에 폐기를 못 할 뿐이고, 그것 때문에 가입이 안 되는 쪽이 더 나쁘다.
	 */
	public String exchangeRefreshToken(String authorizationCode) {
		if (!isEnabled() || authorizationCode == null || authorizationCode.isBlank()) {
			return null;
		}

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("client_id", clientId);
		form.add("client_secret", createClientSecret());
		form.add("grant_type", "authorization_code");
		form.add("code", authorizationCode);

		try {
			TokenResponse response = restClient.post()
				.uri(tokenUrl)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(TokenResponse.class);

			if (response == null || response.refreshToken() == null) {
				log.warn("Apple 토큰 교환 응답에 refresh token이 없다");
				return null;
			}
			return response.refreshToken();
		} catch (RuntimeException e) {
			log.warn("Apple 토큰 교환 실패: {}", e.toString());
			return null;
		}
	}

	/**
	 * 탈퇴 시 Apple 토큰을 폐기한다. 실패해도 예외를 던지지 않는다 — 탈퇴는 모든 사용자가 쓰는
	 * 경로라 Apple 장애가 카카오·일반 계정 탈퇴까지 막으면 안 된다. Apple도 재시도를 요구하지 않는다.
	 */
	public void revoke(String refreshToken) {
		if (!isEnabled() || refreshToken == null || refreshToken.isBlank()) {
			return;
		}

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("client_id", clientId);
		form.add("client_secret", createClientSecret());
		form.add("token", refreshToken);
		form.add("token_type_hint", "refresh_token");

		try {
			restClient.post()
				.uri(revokeUrl)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.toBodilessEntity();
		} catch (RuntimeException e) {
			log.warn("Apple 토큰 폐기 실패(탈퇴는 그대로 진행): {}", e.toString());
		}
	}

	/** Apple은 비밀번호 대신 개발자 키로 서명한 JWT를 client secret으로 받는다. */
	private String createClientSecret() {
		Date now = Date.from(clock.instant());
		return Jwts.builder()
			.header().keyId(keyId).and()
			.issuer(teamId)
			.subject(clientId)
			.audience().add(AUDIENCE).and()
			.issuedAt(now)
			.expiration(Date.from(clock.instant().plus(CLIENT_SECRET_TTL)))
			.signWith(parsePrivateKey(), Jwts.SIG.ES256)
			.compact();
	}

	/** .p8은 PKCS#8 PEM이다. 환경변수로 넣을 때 줄바꿈이 사라지는 경우가 많아 헤더와 공백을 모두 걷어낸다. */
	private PrivateKey parsePrivateKey() {
		String base64 = privateKeyPem
			.replace("-----BEGIN PRIVATE KEY-----", "")
			.replace("-----END PRIVATE KEY-----", "")
			.replaceAll("\\s", "");
		try {
			byte[] der = Base64.getDecoder().decode(base64);
			return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
		} catch (Exception e) {
			// 설정 실수다. 여기서 잡아 삼키면 폐기가 조용히 안 되는 상태로 운영된다.
			throw new IllegalStateException("Apple 개발자 키(.p8)를 읽을 수 없다", e);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TokenResponse(@JsonProperty("refresh_token") String refreshToken) {
	}
}
