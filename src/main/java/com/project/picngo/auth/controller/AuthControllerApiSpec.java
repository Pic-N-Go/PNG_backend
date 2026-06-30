package com.project.picngo.auth.controller;

import com.project.picngo.auth.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "인증 (Auth)", description = "회원가입, 로그인, 소셜 로그인, 이메일 인증 API")
public interface AuthControllerApiSpec {

	@Operation(summary = "일반 회원가입", description = "이메일 인증이 완료된 사용자 정보를 바탕으로 일반 회원을 생성하고 액세스 토큰을 반환합니다.")
	ResponseEntity<TokenResponse> signUp(@Valid @RequestBody SignUpRequest request);

	@Operation(summary = "일반 로그인", description = "이메일과 비밀번호로 일반 회원을 인증하고 액세스 토큰을 반환합니다.")
	ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request);

	@Operation(summary = "카카오 로그인", description = "카카오 인가 코드로 사용자를 인증하고, 가입 이력이 없으면 소셜 회원을 생성한 뒤 액세스 토큰을 반환합니다.")
	ResponseEntity<TokenResponse> loginWithKakao(@Valid @RequestBody KakaoLoginRequest request);

	@Operation(summary = "이메일 인증 코드 발송", description = "아직 가입되지 않은 이메일 주소로 인증 코드를 발송합니다.")
	ResponseEntity<EmailVerificationResponse> sendEmailVerificationCode(@Valid @RequestBody EmailVerificationRequest request);

	@Operation(summary = "이메일 인증 코드 확인", description = "사용자가 입력한 인증 코드를 확인하고 이메일 인증 완료 상태로 처리합니다.")
	ResponseEntity<EmailVerificationResponse> confirmEmailVerificationCode(@Valid @RequestBody EmailConfirmRequest request);

	@Operation(summary = "닉네임 중복 확인", description = "요청한 닉네임을 사용할 수 있는지 확인합니다.")
	ResponseEntity<NicknameCheckResponse> checkNickname(
		@Parameter(description = "중복 확인할 닉네임") @RequestParam String value
	);

	@Operation(summary = "비밀번호 재설정 인증 코드 발송", description = "가입된 이메일로 비밀번호 재설정 인증 코드를 발송합니다.")
	ResponseEntity<EmailVerificationResponse> sendPasswordResetCode(
			@Valid @RequestBody PasswordResetCodeRequest request
	);

	@Operation(summary = "비밀번호 재설정", description = "이메일과 인증 코드, 새 비밀번호를 검증하여 비밀번호를 변경합니다.")
	ResponseEntity<Void> resetPassword(
			@Valid @RequestBody PasswordResetRequest request
	);
}
