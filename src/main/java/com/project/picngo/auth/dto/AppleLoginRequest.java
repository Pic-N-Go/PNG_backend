package com.project.picngo.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * authorizationCode는 선택이다. 탈퇴 시 Apple 토큰을 폐기하는 데만 쓰이고, Apple이 주지 않는
 * 경우가 있어 필수로 걸면 그때 로그인 자체가 막힌다. 폐기를 못 하는 쪽이 덜 나쁘다.
 */
public record AppleLoginRequest(
	@NotBlank
	String identityToken,

	String authorizationCode
) {
}
