package com.project.picngo.auth.service;

import com.project.picngo.auth.domain.EmailVerificationPurpose;
import com.project.picngo.auth.dto.*;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AuthErrorCode;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.dto.UserResponse;
import com.project.picngo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private final UserService userService;
	private final KakaoAuthClient kakaoAuthClient;
	private final JwtTokenProvider jwtTokenProvider;
	private final EmailVerificationService emailVerificationService;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public TokenResponse signUp(SignUpRequest request) {
		emailVerificationService.validateVerified(request.email());

		User user = userService.createLocalUser(
			request.email(),
			passwordEncoder.encode(request.password()),
			request.nickname(),
				request.spotCategories()
		);

		return createTokenResponse(user);
	}

	public TokenResponse login(LoginRequest request) {
		User user = userService.findByEmail(request.email())
				.orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_LOGIN));

		if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new CustomException(AuthErrorCode.INVALID_LOGIN);
		}

		return createTokenResponse(user);
	}

	@Transactional
	public TokenResponse loginWithKakao(KakaoLoginRequest request) {
		KakaoProfile profile = kakaoAuthClient.getProfile(request.accessToken());
		User user = userService.getOrCreateSocialUser(
			profile.email(),
			profile.nickname(),
			profile.profileImageUrl(),
			SocialProvider.KAKAO,
			profile.providerId()
		);

		return createTokenResponse(user);
	}

	public EmailVerificationResponse sendEmailVerificationCode(EmailVerificationRequest request) {
		if (userService.existsByEmail(request.email())) {
			throw new CustomException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
		}

		return emailVerificationService.issueCode(request.email(), EmailVerificationPurpose.SIGN_UP);
	}

	public EmailVerificationResponse confirmEmailVerificationCode(EmailConfirmRequest request) {
		return emailVerificationService.confirmCode(request.email(), request.code());
	}

	public NicknameCheckResponse checkNickname(String nickname) {
		return new NicknameCheckResponse(nickname, !userService.existsByNickname(nickname));
	}

	private TokenResponse createTokenResponse(User user) {
		String accessToken = jwtTokenProvider.createAccessToken(user);
		log.info("\n==================================================" +
				"\n[🔑 로그인 성공 JWT 토큰 수신]" +
				"\n- UserId: {} ({})" +
				"\n- Access Token: Bearer {}" +
				"\n==================================================",
				user.getId(), user.getEmail(), accessToken);
		return TokenResponse.bearer(
			accessToken,
			jwtTokenProvider.getAccessTokenExpirationSeconds(),
			UserResponse.from(user)
		);
	}

	public EmailVerificationResponse sendPasswordResetCode(PasswordResetCodeRequest request) {
		userService.getByEmail(request.email());
		return emailVerificationService.issueCode(request.email(), EmailVerificationPurpose.PASSWORD_RESET);
	}

	@Transactional
	public void resetPassword(PasswordResetRequest request) {
		emailVerificationService.confirmCode(request.email(), request.code());

		User user = userService.getByEmail(request.email());
		user.updatePassword(passwordEncoder.encode(request.newPassword()));
	}

}
