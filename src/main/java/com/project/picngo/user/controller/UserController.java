package com.project.picngo.user.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.spot.dto.MyReviewListResponse;
import com.project.picngo.spot.service.ReviewService;
import com.project.picngo.user.dto.UserSpotCategoryUpdateRequest;
import com.project.picngo.user.dto.UserResponse;
import com.project.picngo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;



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
}
