package com.project.picngo.user.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.spot.dto.MyReviewListResponse;
import com.project.picngo.user.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import com.project.picngo.user.dto.UserStatsResponse;

import java.util.List;

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

	@Operation(summary = "내 프로필 수정", description = "현재 인증된 사용자의 닉네임과 프로필 이미지를 수정합니다")
	ResponseEntity<UserResponse> updateMe(
			@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
			@RequestBody UserProfileUpdateRequest request
	);

	@Operation(
			summary = "타 유저 프로필 조회",
			description = "특정 사용자의 공개 프로필 정보와 팔로워·팔로잉 수를 조회합니다."
	)
	ResponseEntity<UserProfileResponse> getUserProfile(
			@Parameter(description = "조회할 사용자 ID") @PathVariable Long id
	);

	@Operation(
			summary = "팔로우",
			description = "현재 인증된 사용자가 특정 사용자를 팔로우합니다."
	)
	ResponseEntity<Void> follow(
			@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
			@Parameter(description = "팔로우할 사용자 ID") @PathVariable Long id
	);

	@Operation(
			summary = "언팔로우",
			description = "현재 인증된 사용자가 특정 사용자 팔로우를 취소합니다."
	)
	ResponseEntity<Void> unfollow(
			@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
			@Parameter(description = "언팔로우할 사용자 ID") @PathVariable Long id
	);

	@Operation(
			summary = "팔로워 목록 조회",
			description = "특정 사용자를 팔로우하는 사용자 목록을 조회합니다."
	)
	ResponseEntity<List<FollowUserResponse>> getFollowers(
			@Parameter(description = "조회할 사용자 ID") @PathVariable Long id
	);

	@Operation(
			summary = "팔로잉 목록 조회",
			description = "특정 사용자가 팔로우 중인 사용자 목록을 조회합니다."
	)
	ResponseEntity<List<FollowUserResponse>> getFollowing(
			@Parameter(description = "조회할 사용자 ID") @PathVariable Long id
	);

    @Operation(
            summary = "내 활동 통계 조회",
            description = "현재 인증된 사용자의 팔로워 수, 팔로잉 수, 리뷰 수, 방문 장소 수를 조회합니다."
    )
    ResponseEntity<UserStatsResponse> getMyStats(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );
}
