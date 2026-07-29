package com.project.picngo.user.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.spot.dto.MyReviewListResponse;
import com.project.picngo.user.dto.UserResponse;
import com.project.picngo.user.dto.UserSpotCategoryUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "사용자 (User)", description = "사용자 정보 조회 API")
public interface UserControllerApiSpec {

	@Operation(summary = "내 리뷰 목록 조회", description = "현재 인증된 사용자가 작성한 리뷰를 최신순/별점순으로 조회합니다. sort: LATEST, RATING_HIGH, RATING_LOW")
	ResponseEntity<MyReviewListResponse> myReviews(
		@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
		@Parameter(description = "정렬 기준") @RequestParam(defaultValue = "LATEST") String sort,
		@Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
		@Parameter(description = "페이지 크기 (최대 100)") @RequestParam(defaultValue = "20") int size
	);

	@Operation(summary = "내 정보 조회", description = "현재 인증된 사용자의 기본 정보를 조회합니다.")
	ResponseEntity<UserResponse> me(
		@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
	);

	@Operation(summary = "내 관심테마 수정", description = "현재 인증된 사용자의 관심 테마를 수정합니다.")
	ResponseEntity<UserResponse> updateSpotCategories(
			@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
			@RequestBody UserSpotCategoryUpdateRequest request
	);
}
