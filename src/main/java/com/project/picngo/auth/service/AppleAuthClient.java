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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Sign in with Apple의 identity token(JWT)을 검증한다.
 *
 * 카카오와 달리 Apple에는 "토큰으로 프로필을 조회하는 API"가 없다. 앱이 받은 identity token
 * 자체가 Apple이 서명한 증명서이므로, Apple 공개 키로 서명을 확인하고 클레임을 읽는다.
 */
@Component
public class AppleAuthClient {

	private static final String ISSUER = "https://appleid.apple.com";
	// 모르는 kid가 올 때마다 Apple을 호출하면 조작된 토큰으로 호출을 무한히 유발할 수 있다.
	private static final Duration REFETCH_INTERVAL = Duration.ofMinutes(1);

	private final RestClient restClient;
	private final String jwksUrl;
	private final String clientId;

	private volatile Map<String, Key> keys = Map.of();
	private volatile Instant lastFetchedAt = Instant.EPOCH;

	public AppleAuthClient(
		RestClient.Builder restClientBuilder,
		@Value("${apple.auth.jwks-url}") String jwksUrl,
		@Value("${apple.auth.client-id}") String clientId
	) {
		this.restClient = restClientBuilder.build();
		this.jwksUrl = jwksUrl;
		this.clientId = clientId;
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
				.build()
				.parseSignedClaims(identityToken)
				.getPayload();
		} catch (JwtException | IllegalArgumentException e) {
			throw new CustomException(AuthErrorCode.APPLE_IDENTITY_TOKEN_INVALID);
		}

		String providerId = claims.getSubject();
		if (providerId == null || providerId.isBlank()) {
			throw new CustomException(AuthErrorCode.APPLE_IDENTITY_TOKEN_INVALID);
		}

		// 이메일 가리기를 고르면 @privaterelay.appleid.com 주소가 온다. 그대로 저장해도 된다.
		String email = claims.get("email", String.class);
		if (email == null || email.isBlank()) {
			email = providerId + "@apple.local";
		}
		return new AppleProfile(providerId, email);
	}

	private Key findKey(String keyId) {
		Key key = keys.get(keyId);
		// Apple은 키를 주기적으로 교체한다. 모르는 kid면 한 번 다시 받아온다.
		if (key == null && Instant.now().isAfter(lastFetchedAt.plus(REFETCH_INTERVAL))) {
			refreshKeys();
			key = keys.get(keyId);
		}
		if (key == null) {
			throw new JwtException("unknown Apple key id");
		}
		return key;
	}

	private synchronized void refreshKeys() {
		String json;
		try {
			json = restClient.get().uri(jwksUrl).retrieve().body(String.class);
		} catch (RestClientException e) {
			throw new CustomException(AuthErrorCode.APPLE_KEYS_FETCH_FAILED);
		}
		if (json == null) {
			throw new CustomException(AuthErrorCode.APPLE_KEYS_FETCH_FAILED);
		}

		Map<String, Key> fetched = new HashMap<>();
		for (Jwk<?> jwk : Jwks.setParser().build().parse(json).getKeys()) {
			fetched.put(jwk.getId(), jwk.toKey());
		}
		keys = fetched;
		lastFetchedAt = Instant.now();
	}
}
