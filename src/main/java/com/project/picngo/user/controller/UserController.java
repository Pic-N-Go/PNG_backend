package com.project.picngo.user.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.user.dto.UserProfileResponse;
import com.project.picngo.user.dto.UserProfileUpdateRequest;
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
			@RequestBody UserProfileUpdateRequest request
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
}
