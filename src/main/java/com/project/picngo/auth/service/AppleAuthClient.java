package com.project.picngo.auth.service;

import com.project.picngo.auth.dto.AppleProfile;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AuthErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.Jwks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Sign in with Apple의 identity token(JWT)을 검증한다.
 *
 * 카카오와 달리 Apple에는 "토큰으로 프로필을 조회하는 API"가 없다. 앱이 받은 identity token
 * 자체가 Apple이 서명한 증명서이므로, Apple 공개 키로 서명을 확인하고 클레임을 읽는다.
 *
 * ponytail: nonce는 검증하지 않는다 — 가로챈 토큰은 만료 전까지 재사용할 수 있다. 막으려면
 * 서버가 nonce를 발급·보관하고 토큰의 nonce 클레임과 대조해야 한다(요청 형태가 바뀐다).
 */
@Slf4j
@Component
public class AppleAuthClient {

	private static final String ISSUER = "https://appleid.apple.com";
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	/**
	 * 공개 키를 다시 받아오는 최소 간격. 모르는 kid가 올 때마다 호출하면 조작된 토큰이나
	 * Apple 장애 때 요청 수만큼 외부 호출이 나간다. 대신 Apple이 키를 교체한 직후
	 * 최대 이 시간만큼은 새 키로 서명된 토큰이 거부될 수 있다.
	 */
	private static final Duration REFETCH_INTERVAL = Duration.ofMinutes(1);

	private final RestClient restClient;
	private final String jwksUrl;
	private final String clientId;
	private final Clock clock;

	private volatile Map<String, Key> keys = Map.of();
	private volatile Instant lastAttemptAt = Instant.EPOCH;

	@Autowired
	public AppleAuthClient(
		RestClient.Builder restClientBuilder,
		@Value("${apple.auth.jwks-url}") String jwksUrl,
		@Value("${apple.auth.client-id}") String clientId
	) {
		this(restClientBuilder, jwksUrl, clientId, Clock.systemUTC());
	}

	AppleAuthClient(RestClient.Builder restClientBuilder, String jwksUrl, String clientId, Clock clock) {
		// 타임아웃이 없으면 Apple 응답이 늦을 때 로그인 스레드가 그대로 묶인다.
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(TIMEOUT);
		requestFactory.setReadTimeout(TIMEOUT);
		this.restClient = restClientBuilder.requestFactory(requestFactory).build();
		this.jwksUrl = jwksUrl;
		this.clientId = clientId;
		this.clock = clock;
	}

	public AppleProfile getProfile(String identityToken) {
		Claims claims;
		try {
			claims = Jwts.parser()
				.keyLocator(new LocatorAdapter<>() {
					@Override
					protected Key locate(JwsHeader header) {
						return findKey(header.getKeyId());
					}
				})
				.requireIssuer(ISSUER)
				// aud가 우리 앱이 아니면 다른 앱이 발급받은 토큰이다. 이 검사가 없으면
				// 아무 앱에서나 받은 Apple 토큰으로 우리 서비스에 로그인할 수 있다.
				.requireAudience(clientId)
				.clock(() -> java.util.Date.from(clock.instant()))
				.build()
				.parseSignedClaims(identityToken)
				.getPayload();
		} catch (JwtException | IllegalArgumentException e) {
			throw invalidToken(e.getMessage());
		}

		// jjwt는 exp가 있을 때만 만료를 본다. 없는 토큰은 영구 토큰이 되므로 따로 막는다.
		if (claims.getExpiration() == null) {
			throw invalidToken("exp 없음");
		}
		String providerId = claims.getSubject();
		if (providerId == null || providerId.isBlank()) {
			throw invalidToken("sub 없음");
		}

		// 이메일 가리기를 고르면 @privaterelay.appleid.com 주소가 온다. 그대로 저장해도 된다.
		String email = claims.get("email", String.class);
		if (email == null || email.isBlank()) {
			email = providerId + "@apple.local";
		}
		return new AppleProfile(providerId, email);
	}

	/** 사유를 남기지 않으면 번들 ID 불일치 같은 설정 문제를 401만 보고는 알 수 없다. 토큰 자체는 남기지 않는다. */
	private CustomException invalidToken(String reason) {
		log.warn("Apple identity token 거부: {}", reason);
		return new CustomException(AuthErrorCode.APPLE_IDENTITY_TOKEN_INVALID);
	}

	private Key findKey(String keyId) {
		if (keyId == null) {
			throw new JwtException("kid 없음");
		}
		Key key = keys.get(keyId);
		// Apple은 키를 주기적으로 교체한다. 모르는 kid면 다시 받아온다.
		if (key == null) {
			refreshKeysIfStale();
			key = keys.get(keyId);
		}
		if (key == null) {
			// 키를 한 번도 못 받았다면 토큰 문제가 아니라 Apple 조회 실패다.
			if (keys.isEmpty()) {
				throw new CustomException(AuthErrorCode.APPLE_KEYS_FETCH_FAILED);
			}
			throw new JwtException("알 수 없는 kid: " + keyId);
		}
		return key;
	}

	private synchronized void refreshKeysIfStale() {
		// 락을 기다린 스레드는 앞선 스레드가 방금 받아온 결과를 그대로 쓴다.
		if (clock.instant().isBefore(lastAttemptAt.plus(REFETCH_INTERVAL))) {
			return;
		}
		// 성공 여부와 무관하게 시도 시각을 남긴다. 실패 때 남기지 않으면 장애 내내 요청마다 호출한다.
		lastAttemptAt = clock.instant();

		try {
			String json = restClient.get().uri(jwksUrl).retrieve().body(String.class);
			Map<String, Key> fetched = new HashMap<>();
			for (Jwk<?> jwk : Jwks.setParser().build().parse(json).getKeys()) {
				fetched.put(jwk.getId(), jwk.toKey());
			}
			if (fetched.isEmpty()) {
				throw new IllegalStateException("빈 JWKS");
			}
			keys = fetched;
		} catch (RuntimeException e) {
			// 네트워크 오류와 JWKS가 아닌 응답을 모두 여기서 잡는다. 기존 키는 버리지 않는다.
			log.warn("Apple 공개 키 조회 실패: {}", e.toString());
			throw new CustomException(AuthErrorCode.APPLE_KEYS_FETCH_FAILED);
		}
	}
}
