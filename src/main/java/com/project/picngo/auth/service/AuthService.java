package com.project.picngo.auth.service;

import com.project.picngo.auth.dto.EmailConfirmRequest;
import com.project.picngo.auth.dto.EmailVerificationRequest;
import com.project.picngo.auth.dto.EmailVerificationResponse;
import com.project.picngo.auth.dto.KakaoLoginRequest;
import com.project.picngo.auth.dto.KakaoProfile;
import com.project.picngo.auth.dto.LoginRequest;
import com.project.picngo.auth.dto.NicknameCheckResponse;
import com.project.picngo.auth.dto.SignUpRequest;
import com.project.picngo.auth.dto.TokenResponse;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.dto.UserResponse;
import com.project.picngo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
			request.nickname()
		);

		return createTokenResponse(user);
	}

	public TokenResponse login(LoginRequest request) {
		User user = userService.getByEmail(request.email());

		if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
		}

		return createTokenResponse(user);
	}

	@Transactional
	public TokenResponse loginWithKakao(KakaoLoginRequest request) {
		KakaoProfile profile = kakaoAuthClient.getProfile(request.code());
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
			throw new IllegalArgumentException("이미 가입된 이메일입니다.");
		}

		return emailVerificationService.issueCode(request.email());
	}

	public EmailVerificationResponse confirmEmailVerificationCode(EmailConfirmRequest request) {
		return emailVerificationService.confirmCode(request.email(), request.code());
	}

	public NicknameCheckResponse checkNickname(String nickname) {
		return new NicknameCheckResponse(nickname, !userService.existsByNickname(nickname));
	}

	private TokenResponse createTokenResponse(User user) {
		String accessToken = jwtTokenProvider.createAccessToken(user);
		return TokenResponse.bearer(
			accessToken,
			jwtTokenProvider.getAccessTokenExpirationSeconds(),
			UserResponse.from(user)
		);
	}
}
