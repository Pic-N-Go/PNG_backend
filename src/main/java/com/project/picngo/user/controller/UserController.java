package com.project.picngo.user.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.spot.dto.MyReviewListResponse;
import com.project.picngo.spot.service.ReviewService;
import com.project.picngo.user.dto.*;
import com.project.picngo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController implements UserControllerApiSpec {

	private final UserService userService;
	private final ReviewService reviewService;

	@GetMapping("/me/reviews")
	public ResponseEntity<MyReviewListResponse> myReviews(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@RequestParam(defaultValue = "LATEST") String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return ResponseEntity.ok(reviewService.getMyReviews(userDetails.getId(), sort, page, size));
	}

	@GetMapping("/me")
	public ResponseEntity<UserResponse> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
		return ResponseEntity.ok(userService.getMyInfo(userDetails.getId()));
	}

	@PatchMapping("/me/spot-categories")
	public ResponseEntity<UserResponse> updateSpotCategories(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@RequestBody UserSpotCategoryUpdateRequest request
			){
		return ResponseEntity.ok(
				userService.updateUserSpotCategories(userDetails.getId(), request.spotCategories())
		);
	}

	// 내 프로필 수정 API
	@PutMapping("/me")
	public ResponseEntity<UserResponse> updateMe(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@Valid @RequestBody UserProfileUpdateRequest request
			){
		return ResponseEntity.ok(
				userService.updateMyProfile(userDetails.getId(), request)
		);
	}

	// 타 유저 프로필 조회 API
	@GetMapping("/{id}/profile")
	public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable Long id){
		return ResponseEntity.ok(userService.getUserProfile(id));
	}

	// 팔로우 API
	@PostMapping("/{id}/follow")
	public ResponseEntity<Void> follow(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@PathVariable Long id
	){
		userService.follow(userDetails.getId(), id);
		return ResponseEntity.ok().build();
	}

	// 언팔로우 API
	@DeleteMapping("/{id}/follow")
	public ResponseEntity<Void> unfollow(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@PathVariable Long id
	){
		userService.unfollow(userDetails.getId(), id);
		return ResponseEntity.ok().build();
	}

	// 팔로워 목록 조회 API
	@GetMapping("/{id}/followers")
	public ResponseEntity<List<FollowUserResponse>> getFollowers(@PathVariable Long id){
		return ResponseEntity.ok(userService.getFollowers(id));
	}

	// 팔로잉 목록 조회 API
	@GetMapping("/{id}/following")
	public ResponseEntity<List<FollowUserResponse>> getFollowing(@PathVariable Long id){
		return ResponseEntity.ok(userService.getFollowing(id));
	}

    // 내 활동 통계 조회 API
    @GetMapping("/me/stats")
    public ResponseEntity<UserStatsResponse> getMyStats(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ){
        return ResponseEntity.ok(userService.getMyStats(userDetails.getId()));
    }
}
