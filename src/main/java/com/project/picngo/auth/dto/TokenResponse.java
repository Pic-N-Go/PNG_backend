package com.project.picngo.auth.dto;

import com.project.picngo.user.dto.UserResponse;

public record TokenResponse(
	String tokenType,
	String accessToken,
	Long expiresIn,
	UserResponse user
) {

	public static TokenResponse bearer(String accessToken, Long expiresIn, UserResponse user) {
		return new TokenResponse("Bearer", accessToken, expiresIn, user);
	}
}
