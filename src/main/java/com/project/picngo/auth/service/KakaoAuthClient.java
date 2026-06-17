package com.project.picngo.auth.service;

import com.project.picngo.auth.dto.KakaoProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class KakaoAuthClient {

	private final RestClient restClient;

	public KakaoAuthClient(RestClient.Builder restClientBuilder) {
		this.restClient = restClientBuilder.build();
	}

	@Value("${kakao.auth.token-url}")
	private String tokenUrl;

	@Value("${kakao.auth.user-info-url}")
	private String userInfoUrl;

	@Value("${kakao.auth.client-id}")
	private String clientId;

	@Value("${kakao.auth.client-secret}")
	private String clientSecret;

	@Value("${kakao.auth.redirect-uri}")
	private String redirectUri;

	public KakaoProfile getProfile(String authorizationCode) {
		String accessToken = requestAccessToken(authorizationCode);
		return requestProfile(accessToken);
	}

	private String requestAccessToken(String authorizationCode) {
		validateKakaoProperties();

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", clientId);
		form.add("redirect_uri", redirectUri);
		form.add("code", authorizationCode);

		if (!clientSecret.isBlank()) {
			form.add("client_secret", clientSecret);
		}

		Map<String, Object> response = restClient
			.post()
			.uri(tokenUrl)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(new ParameterizedTypeReference<>() {
			});

		if (response == null || response.get("access_token") == null) {
			throw new IllegalStateException("카카오 액세스 토큰을 발급받지 못했습니다.");
		}

		return response.get("access_token").toString();
	}

	@SuppressWarnings("unchecked")
	private KakaoProfile requestProfile(String accessToken) {
		Map<String, Object> response = restClient
			.get()
			.uri(userInfoUrl)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.retrieve()
			.body(new ParameterizedTypeReference<>() {
			});

		if (response == null || response.get("id") == null) {
			throw new IllegalStateException("카카오 사용자 정보를 조회하지 못했습니다.");
		}

		String providerId = response.get("id").toString();
		Map<String, Object> kakaoAccount = (Map<String, Object>) response.getOrDefault("kakao_account", Map.of());
		Map<String, Object> profile = (Map<String, Object>) kakaoAccount.getOrDefault("profile", Map.of());

		String email = valueOrDefault(kakaoAccount.get("email"), providerId + "@kakao.local");
		String nickname = valueOrDefault(profile.get("nickname"), "kakao_" + providerId);
		String profileImageUrl = valueOrDefault(profile.get("profile_image_url"), null);

		return new KakaoProfile(providerId, email, nickname, profileImageUrl);
	}

	private void validateKakaoProperties() {
		if (clientId.isBlank() || redirectUri.isBlank()) {
			throw new IllegalStateException("카카오 로그인 설정이 필요합니다.");
		}
	}

	private String valueOrDefault(Object value, String defaultValue) {
		if (value == null || value.toString().isBlank()) {
			return defaultValue;
		}

		return value.toString();
	}
}
