package com.project.picngo.auth.service;

import com.project.picngo.auth.dto.KakaoProfile;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AuthErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class KakaoAuthClient {

	private final RestClient restClient;

	public KakaoAuthClient(RestClient.Builder restClientBuilder) {
		this.restClient = restClientBuilder.build();
	}

	@Value("${kakao.auth.user-info-url}")
	private String userInfoUrl;

	public KakaoProfile getProfile(String accessToken) {
		return requestProfile(accessToken);
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
			throw new CustomException(AuthErrorCode.KAKAO_PROFILE_FETCH_FAILED);
		}

		String providerId = response.get("id").toString();
		Map<String, Object> kakaoAccount = (Map<String, Object>) response.getOrDefault("kakao_account", Map.of());
		Map<String, Object> profile = (Map<String, Object>) kakaoAccount.getOrDefault("profile", Map.of());

		String email = valueOrDefault(kakaoAccount.get("email"), providerId + "@kakao.local");
		String nickname = valueOrDefault(profile.get("nickname"), "kakao_" + providerId);
		String profileImageUrl = toHttps(valueOrDefault(profile.get("profile_image_url"), null));

		return new KakaoProfile(providerId, email, nickname, profileImageUrl);
	}

	// 카카오가 프로필 이미지를 http로 내려주는 경우가 있다.
	// iOS ATS / Android cleartext 정책상 http는 앱에서 로드되지 않으므로 저장 전에 https로 승격한다.
	private String toHttps(String url) {
		if (url == null) {
			return null;
		}

		return url.replaceFirst("^http://", "https://");
	}

	private String valueOrDefault(Object value, String defaultValue) {
		if (value == null || value.toString().isBlank()) {
			return defaultValue;
		}

		return value.toString();
	}
}
