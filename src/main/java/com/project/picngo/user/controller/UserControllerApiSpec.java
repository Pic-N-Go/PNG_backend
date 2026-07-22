package com.project.picngo.user.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.user.dto.UserProfileResponse;
import com.project.picngo.user.dto.UserProfileUpdateRequest;
import com.project.picngo.user.dto.UserResponse;
import com.project.picngo.user.dto.UserSpotCategoryUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "사용자 (User)", description = "사용자 정보 조회 API")
public interface UserControllerApiSpec {

	@Operation(summary = "내 정보 조회", description = "현재 인증된 사용자의 기본 정보를 조회합니다.")
	ResponseEntity<UserResponse> me(
		@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
	);

	@Operation(summary = "내 관심테마 수정", description = "현재 인증된 사용자의 관심 테마를 수정합니다.")
	ResponseEntity<UserResponse> updateSpotCategories(
			@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
			@RequestBody UserSpotCategoryUpdateRequest request
	);

	@Operation(summary = "내 프로필 수정", description = "현재 인증된 사용자의 닉네임과 프로필 이미지를 수정합니다")
	ResponseEntity<UserResponse> updateMe(
			@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
			@RequestBody UserProfileUpdateRequest request
	);

	@Operation(
			summary = "타 유저 프로필 조회",
			description = "특정 사용자의 공개 프로필 정보를 조회합니다."
	)
	ResponseEntity<UserProfileResponse> getUserProfile(
			@Parameter(description = "조회할 사용자 ID") @PathVariable Long id
	);
}
