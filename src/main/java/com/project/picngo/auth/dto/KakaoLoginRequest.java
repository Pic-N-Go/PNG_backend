package com.project.picngo.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
	@NotBlank
	String accessToken
) {
}
