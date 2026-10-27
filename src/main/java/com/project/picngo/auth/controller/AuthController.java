package com.project.picngo.auth.controller;

import com.project.picngo.auth.dto.*;
import com.project.picngo.auth.service.AppleAuthClient;
import com.project.picngo.auth.service.AppleTokenClient;
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
	private final AppleAuthClient appleAuthClient;
	private final AppleTokenClient appleTokenClient;

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

	// 탈퇴 계정 복구. 탈퇴 계정은 토큰을 못 받으므로 인증 없이 자격증명으로 시작한다.
	@PostMapping("/restore")
	public ResponseEntity<TokenResponse> restore(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(authService.restore(request));
	}

	@PostMapping("/restore/social")
	public ResponseEntity<TokenResponse> restoreWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
		return ResponseEntity.ok(authService.restoreWithKakao(request));
	}

	// Apple 토큰 검증은 외부 호출(공개 키 조회)을 포함하므로 서비스의 DB 트랜잭션 밖에서 끝낸다.
	@PostMapping("/login/apple")
	public ResponseEntity<TokenResponse> loginWithApple(@Valid @RequestBody AppleLoginRequest request) {
		return ResponseEntity.ok(authService.loginWithApple(
			appleAuthClient.getProfile(request.identityToken()),
			appleTokenClient.exchangeRefreshToken(request.authorizationCode())
		));
	}

	@PostMapping("/restore/apple")
	public ResponseEntity<TokenResponse> restoreWithApple(@Valid @RequestBody AppleLoginRequest request) {
		return ResponseEntity.ok(authService.restoreWithApple(
			appleAuthClient.getProfile(request.identityToken()),
			appleTokenClient.exchangeRefreshToken(request.authorizationCode())
		));
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
