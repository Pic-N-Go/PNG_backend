package com.project.picngo.auth.service;

import com.project.picngo.auth.domain.EmailVerificationPurpose;
import com.project.picngo.auth.dto.*;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.common.exception.code.AuthErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
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
    private final RefreshTokenService refreshTokenService;
	private final ImageStorageService imageStorageService;

	@Transactional
	public TokenResponse signUp(SignUpRequest request) {
		emailVerificationService.validateVerified(request.email());

		User user = userService.createLocalUser(
			request.email(),
			passwordEncoder.encode(request.password()),
			request.nickname(),
				request.spotCategories()
		);

		// 이메일 가입은 가입 화면에서 이미 닉네임을 받았으므로 온보딩이 필요 없다.
		return createTokenResponse(user, false);
	}

	public TokenResponse login(LoginRequest request) {
		User user = verifyCredentials(request);

		// 탈퇴 계정에는 토큰을 발급하지 않는다. 복구는 /auth/restore로만 가능하다 —
		// 토큰을 주고 나머지 API를 필터로 막는 방식은 화이트리스트가 필요하고 우회 위험이 있다.
		if (user.isWithdrawn()) {
			throw new CustomException(AuthErrorCode.ACCOUNT_WITHDRAWN);
		}

		return createTokenResponse(user, false);
	}

	/**
	 * 탈퇴 취소. 로그인과 같은 자격증명을 받아 검증한 뒤 되돌린다 — 탈퇴 계정은 토큰을 받을 수
	 * 없으므로, 인증이 필요한 경로로는 복구를 시작할 수 없다.
	 */
	@Transactional
	public TokenResponse restore(LoginRequest request) {
		User user = verifyCredentials(request);
		userService.restore(user);
		return createTokenResponse(user, false);
	}

	/** 카카오 계정 복구. 소셜 계정은 비밀번호가 없어 accessToken으로 본인 확인한다. */
	@Transactional
	public TokenResponse restoreWithKakao(KakaoLoginRequest request) {
		KakaoProfile profile = kakaoAuthClient.getProfile(request.accessToken());
		User user = userService.findByProviderAndProviderId(SocialProvider.KAKAO, profile.providerId())
				.orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

		userService.restore(user);
		return createTokenResponse(user, false);
	}

	/**
	 * 이메일·비밀번호 검증만 한다(탈퇴 여부는 보지 않는다). 로그인과 복구가 같은 검증을 쓰되
	 * 탈퇴 계정 처리만 다르기 때문이다.
	 */
	private User verifyCredentials(LoginRequest request) {
		User user = userService.findByEmail(request.email())
				.orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_LOGIN));

		if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new CustomException(AuthErrorCode.INVALID_LOGIN);
		}
		return user;
	}

	@Transactional
	public TokenResponse loginWithKakao(KakaoLoginRequest request) {
		KakaoProfile profile = kakaoAuthClient.getProfile(request.accessToken());
		UserService.SocialUserResult result = userService.getOrCreateSocialUser(
			profile.email(),
			profile.nickname(),
			profile.profileImageUrl(),
			SocialProvider.KAKAO,
			profile.providerId()
		);

		return createTokenResponse(result.user(), result.newUser());
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

	private TokenResponse createTokenResponse(User user, boolean isNewUser) {
		String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = jwtTokenProvider.createRefreshToken(user);

        refreshTokenService.saveRefreshToken(refreshToken, user.getId());
		log.info("\n==================================================" +
				"\n[🔑 로그인 성공 JWT 토큰 수신]" +
				"\n- UserId: {} ({})" +
				"\n- Access Token: Bearer {}" +
				"\n==================================================",
				user.getId(), user.getEmail(), accessToken);
		return TokenResponse.bearer(
			accessToken,
			jwtTokenProvider.getAccessTokenExpirationSeconds(),
            refreshToken,
                jwtTokenProvider.getRefreshTokenExpirationSeconds(),
			UserResponse.from(user, imageStorageService.getPresignedUrl(user.getDisplayProfileImage())),
			isNewUser
		);
	}

    public TokenResponse reissueTokens(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);

        if(!refreshTokenService.consumeRefreshToken(refreshToken, userId)) {
            throw new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userService.getById(userId);

        // 토큰 재발급은 기존 계정이므로 항상 false다.
        return createTokenResponse(user, false);
    }

	public EmailVerificationResponse sendPasswordResetCode(PasswordResetCodeRequest request) {
		User user = userService.getByEmail(request.email());
		// 소셜 계정은 비밀번호로 로그인하지 않는다. 막지 않으면 이 흐름으로 소셜 전용 계정에
		// 비밀번호가 생겨, 의도한 적 없는 이메일 로그인 진입점이 열린다.
		requireLocalAccount(user);
		return emailVerificationService.issueCode(request.email(), EmailVerificationPurpose.PASSWORD_RESET);
	}

	private void requireLocalAccount(User user) {
		if (user.getProvider() != SocialProvider.LOCAL) {
			throw new CustomException(AuthErrorCode.SOCIAL_ACCOUNT_HAS_NO_PASSWORD);
		}
	}

	@Transactional
	public void resetPassword(PasswordResetRequest request) {
		emailVerificationService.confirmCode(request.email(), request.code());

		User user = userService.getByEmail(request.email());
		// 코드 발송을 막아도 이미 받아둔 코드로 여기만 호출할 수 있다 — 실제 교체 지점에서도 검사한다.
		requireLocalAccount(user);
		user.updatePassword(passwordEncoder.encode(request.newPassword()));
		// 설정에서 바꾸는 경로와 같은 이유로 기존 세션을 끊는다.
		refreshTokenService.revokeAllByUserId(user.getId());
	}

}
