package com.project.picngo.auth.service;

import com.project.picngo.auth.dto.KakaoLoginRequest;
import com.project.picngo.auth.dto.KakaoProfile;
import com.project.picngo.auth.dto.RefreshTokenRequest;
import com.project.picngo.auth.dto.TokenResponse;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AuthErrorCode;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private KakaoAuthClient kakaoAuthClient;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("유효한 리프레시 토큰이면 새로운 토큰 쌍을 발급한다")
    void reissuesTokenPairWithValidRefreshToken() {
        RefreshTokenRequest request = new RefreshTokenRequest("old-refresh-token");
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("test@example.com");
        when(jwtTokenProvider.validateRefreshToken("old-refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("old-refresh-token")).thenReturn(1L);
        when(refreshTokenService.consumeRefreshToken("old-refresh-token", 1L)).thenReturn(true);
        when(userService.getById(1L)).thenReturn(user);
        when(jwtTokenProvider.createAccessToken(user)).thenReturn("new-access-token");
        when(jwtTokenProvider.createRefreshToken(user)).thenReturn("new-refresh-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(60L);
        when(jwtTokenProvider.getRefreshTokenExpirationSeconds()).thenReturn(120L);

        TokenResponse response = authService.reissueTokens(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.expiresIn()).isEqualTo(60L);
        assertThat(response.refreshTokenExpiresIn()).isEqualTo(120L);
        // 재발급은 기존 계정이다 — true면 클라이언트가 매번 온보딩을 띄운다.
        assertThat(response.isNewUser()).isFalse();
        verify(refreshTokenService).saveRefreshToken("new-refresh-token", 1L);
    }

    /**
     * isNewUser는 신규 카카오 가입자를 온보딩으로 보낼지 정하는 유일한 신호다.
     * false여야 할 때 true면 기존 사용자가 매 로그인마다 온보딩을 보고,
     * true여야 할 때 false면 신규 사용자가 서버가 임의로 지은 닉네임에 갇힌다.
     * 둘 다 예외 없이 조용히 깨지는 종류라 여기서 못 박는다.
     */
    @Test
    @DisplayName("카카오 신규 가입이면 isNewUser가 true로 전파된다")
    void propagatesIsNewUserForNewKakaoSignUp() {
        assertThat(kakaoLoginResponse(true).isNewUser()).isTrue();
    }

    @Test
    @DisplayName("카카오 기존 계정이면 isNewUser가 false로 전파된다")
    void propagatesIsNewUserForExistingKakaoAccount() {
        assertThat(kakaoLoginResponse(false).isNewUser()).isFalse();
    }

    private TokenResponse kakaoLoginResponse(boolean newUser) {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("test@kakao.local");
        when(kakaoAuthClient.getProfile("kakao-access-token"))
                .thenReturn(new KakaoProfile("provider-1", "test@kakao.local", "홍길동", null));
        when(userService.getOrCreateSocialUser(
                "test@kakao.local", "홍길동", null, SocialProvider.KAKAO, "provider-1"))
                .thenReturn(new UserService.SocialUserResult(user, newUser));
        when(jwtTokenProvider.createAccessToken(user)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(user)).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(60L);
        when(jwtTokenProvider.getRefreshTokenExpirationSeconds()).thenReturn(120L);

        return authService.loginWithKakao(new KakaoLoginRequest("kakao-access-token"));
    }

    @Test
    @DisplayName("JWT 검증에 실패한 리프레시 토큰은 거부한다")
    void rejectsInvalidRefreshToken() {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-refresh-token");
        when(jwtTokenProvider.validateRefreshToken("invalid-refresh-token")).thenReturn(false);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> authService.reissueTokens(request)
        );

        assertSame(AuthErrorCode.INVALID_REFRESH_TOKEN, exception.getErrorCode());
        verify(refreshTokenService, never()).consumeRefreshToken(
                "invalid-refresh-token",
                1L
        );
    }

    @Test
    @DisplayName("이미 사용했거나 Redis에 없는 리프레시 토큰은 거부한다")
    void rejectsConsumedRefreshToken() {
        RefreshTokenRequest request = new RefreshTokenRequest("consumed-refresh-token");
        when(jwtTokenProvider.validateRefreshToken("consumed-refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("consumed-refresh-token")).thenReturn(1L);
        when(refreshTokenService.consumeRefreshToken("consumed-refresh-token", 1L)).thenReturn(false);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> authService.reissueTokens(request)
        );

        assertSame(AuthErrorCode.INVALID_REFRESH_TOKEN, exception.getErrorCode());
        verify(userService, never()).getById(1L);
    }
}
