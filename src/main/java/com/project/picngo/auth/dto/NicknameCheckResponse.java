package com.project.picngo.auth.dto;

public record NicknameCheckResponse(
	String nickname,
	boolean available
) {
}
