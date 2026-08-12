package com.project.picngo.auth.service;

import com.project.picngo.auth.dto.RefreshTokenRequest;
import com.project.picngo.auth.dto.TokenResponse;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AuthErrorCode;
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
        verify(refreshTokenService).saveRefreshToken("new-refresh-token", 1L);
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
