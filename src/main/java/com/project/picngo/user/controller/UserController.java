package com.project.picngo.user.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.user.dto.InterestThemeUpdateRequest;
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

	@PatchMapping("/me/themes")
	public ResponseEntity<UserResponse> updateInterestThemes(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@RequestBody InterestThemeUpdateRequest request
			){
		return ResponseEntity.ok(
				userService.updateInterestTheme(userDetails.getId(), request.interestThemes())
		);
	}
}
