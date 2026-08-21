package com.project.picngo.user.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.spot.dto.MyReviewListResponse;
import com.project.picngo.spot.dto.ReviewedSpotResponse;
import com.project.picngo.user.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
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

	@Operation(summary = "PIC MAP 리뷰 핀 목록 조회",
		description = "내가 리뷰를 남긴 스팟을 지도 핀용으로 조회합니다. 리뷰 작성일 내림차순, 페이징 없음. "
			+ "리뷰 본문·태그·사진은 내려가지 않습니다 — 그것들이 필요하면 GET /users/me/reviews를 쓰세요.")
	ResponseEntity<List<ReviewedSpotResponse>> myReviewedSpots(
		@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
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

	@Operation(
			summary = "내 프로필 수정",
			description = "현재 인증된 사용자의 닉네임·자기소개를 수정합니다. 전체 교체이므로 생략한 값은 비워집니다. "
					+ "프로필 사진은 이 API로 바뀌지 않습니다 — PATCH/DELETE /users/me/profile-image를 쓰세요."
	)
	ResponseEntity<UserResponse> updateMe(
			@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
			@RequestBody UserProfileUpdateRequest request
	);

	@Operation(
			summary = "프로필 사진 교체",
			description = "multipart로 사진을 올려 프로필 사진을 바꿉니다. 이전에 올린 사진은 저장소에서 삭제됩니다."
	)
	ResponseEntity<UserResponse> updateProfileImage(
			@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
			@Parameter(description = "업로드할 이미지") MultipartFile image
	);

	@Operation(summary = "프로필 사진 삭제", description = "프로필 사진을 비웁니다(기본 이미지).")
	ResponseEntity<UserResponse> deleteProfileImage(
			@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
	);

	@Operation(
			summary = "비밀번호 변경",
			description = "현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다. 소셜 계정은 비밀번호가 없어 사용할 수 없습니다."
	)
	ResponseEntity<Void> changePassword(
			@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
			@RequestBody PasswordChangeRequest request
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
			summary = "회원 탈퇴",
			description = "소프트 삭제입니다. 30일 동안은 로그인·조회에서 제외되기만 하고 개인정보는 남아 있어 "
					+ "POST /auth/restore(소셜은 /auth/restore/social)로 복구할 수 있습니다. "
					+ "30일이 지나면 배치가 개인정보를 파기하며, 작성한 게시글·댓글은 '탈퇴한 사용자' 이름으로 유지됩니다."
	)
	ResponseEntity<Void> withdraw(
			@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
	);

	@Operation(
			summary = "사용자 검색",
			description = "닉네임 부분일치로 사용자를 검색합니다. 대소문자를 구분하지 않습니다. 탈퇴 계정은 제외됩니다."
	)
	ResponseEntity<Page<FollowUserResponse>> searchUsers(
			@Parameter(description = "검색어(닉네임)") @RequestParam String keyword,
			@Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
			@Parameter(description = "페이지 크기 (최대 50)") @RequestParam(defaultValue = "20") int size
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
            description = "현재 인증된 사용자의 팔로워 수, 팔로잉 수, 리뷰 수, 방문 장소 수, 게시글 수를 조회합니다.\n\n"
                    + "리뷰 수와 방문 장소 수는 집계 기준이 확정되지 않아 아직 0을 반환합니다."
    )
    ResponseEntity<UserStatsResponse> getMyStats(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );
}
