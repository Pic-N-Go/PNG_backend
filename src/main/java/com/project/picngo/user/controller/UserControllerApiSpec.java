package com.project.picngo.user.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.user.dto.InterestThemeUpdateRequest;
import com.project.picngo.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "사용자 (User)", description = "사용자 정보 조회 API")
public interface UserControllerApiSpec {

	@Operation(summary = "내 정보 조회", description = "현재 인증된 사용자의 기본 정보를 조회합니다.")
	ResponseEntity<UserResponse> me(
		@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
	);

	@Operation(summary = "내 관심테마 수정", description = "현재 인증된 사용자의 관심 테마를 수정합니다.")
	ResponseEntity<UserResponse> updateInterestThemes(
			@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
			@RequestBody InterestThemeUpdateRequest request
	);
}
