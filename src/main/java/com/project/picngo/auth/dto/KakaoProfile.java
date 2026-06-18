package com.project.picngo.auth.dto;

public record KakaoProfile(
	String providerId,
	String email,
	String nickname,
	String profileImageUrl
) {
}
