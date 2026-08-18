package com.project.picngo.auth.dto;

import com.project.picngo.user.dto.UserResponse;

public record TokenResponse(
	String tokenType,
	String accessToken,
	Long expiresIn,
    String refreshToken,
    Long refreshTokenExpiresIn,
	UserResponse user
) {

	public static TokenResponse bearer(
            String accessToken,
            Long expiresIn,
            String refreshToken,
            Long refreshTokenExpiresIn,
            UserResponse user) {
		return new TokenResponse("Bearer", accessToken, expiresIn, refreshToken, refreshTokenExpiresIn, user);
	}
}
