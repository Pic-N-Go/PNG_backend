package com.project.picngo.auth.dto;

import com.project.picngo.user.dto.UserResponse;

public record TokenResponse(
	String tokenType,
	String accessToken,
	Long expiresIn,
    String refreshToken,
    Long refreshTokenExpiresIn,
	UserResponse user,
	/**
	 * 이번 요청으로 계정이 처음 만들어졌는지. 클라이언트는 소셜 로그인에서 이 값이 true일 때만
	 * 온보딩(닉네임·관심 테마 설정)으로 보낸다 — 카카오 닉네임은 중복·특수문자가 흔해
	 * 서버가 임의로 다듬은 값이 그대로 굳지 않게 사용자에게 한 번 확인받는다.
	 */
	boolean isNewUser
) {

	public static TokenResponse bearer(
            String accessToken,
            Long expiresIn,
            String refreshToken,
            Long refreshTokenExpiresIn,
            UserResponse user,
            boolean isNewUser) {
		return new TokenResponse("Bearer", accessToken, expiresIn, refreshToken, refreshTokenExpiresIn, user, isNewUser);
	}
}
