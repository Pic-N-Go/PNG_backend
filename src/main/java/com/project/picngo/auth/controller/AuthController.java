package com.project.picngo.auth.controller;

import com.project.picngo.auth.dto.EmailConfirmRequest;
import com.project.picngo.auth.dto.EmailVerificationRequest;
import com.project.picngo.auth.dto.EmailVerificationResponse;
import com.project.picngo.auth.dto.KakaoLoginRequest;
import com.project.picngo.auth.dto.LoginRequest;
import com.project.picngo.auth.dto.NicknameCheckResponse;
import com.project.picngo.auth.dto.SignUpRequest;
import com.project.picngo.auth.dto.TokenResponse;
import com.project.picngo.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController implements AuthControllerApiSpec {

	private final AuthService authService;

	@PostMapping("/register")
	public ResponseEntity<TokenResponse> signUp(@Valid @RequestBody SignUpRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.signUp(request));
	}

	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(authService.login(request));
	}

	@PostMapping("/login/social")
	public ResponseEntity<TokenResponse> loginWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
		return ResponseEntity.ok(authService.loginWithKakao(request));
	}

	@PostMapping("/email/verify")
	public ResponseEntity<EmailVerificationResponse> sendEmailVerificationCode(@Valid @RequestBody EmailVerificationRequest request) {
		return ResponseEntity.ok(authService.sendEmailVerificationCode(request));
	}

	@PostMapping("/email/confirm")
	public ResponseEntity<EmailVerificationResponse> confirmEmailVerificationCode(
		@Valid @RequestBody EmailConfirmRequest request
	) {
		return ResponseEntity.ok(authService.confirmEmailVerificationCode(request));
	}

	@GetMapping("/nickname/check")
	public ResponseEntity<NicknameCheckResponse> checkNickname(@RequestParam String value) {
		return ResponseEntity.ok(authService.checkNickname(value));
	}
}
