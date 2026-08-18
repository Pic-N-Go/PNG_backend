package com.project.picngo.auth.controller;

import com.project.picngo.auth.dto.*;
import com.project.picngo.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

	@PostMapping("/password/reset/code")
	public ResponseEntity<EmailVerificationResponse> sendPasswordResetCode(
			@Valid @RequestBody PasswordResetCodeRequest request) {
		return ResponseEntity.ok(authService.sendPasswordResetCode(request));
	}

	@PostMapping("/password/reset")
	public ResponseEntity<Void> resetPassword(
			@Valid @RequestBody PasswordResetRequest request
	){
		authService.resetPassword(request);
		return ResponseEntity.noContent().build();
	}

    @PostMapping("/token/refresh")
    public ResponseEntity<TokenResponse> refreshTokens(
            @Valid @RequestBody RefreshTokenRequest request
    ){
        return ResponseEntity.ok(authService.reissueTokens(request));
    }
}
