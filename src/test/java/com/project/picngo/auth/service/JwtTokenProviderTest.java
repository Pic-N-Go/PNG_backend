package com.project.picngo.auth.service;

import com.project.picngo.user.domain.Role;
import com.project.picngo.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private User user;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(
                jwtTokenProvider,
                "secret",
                "test-secret-key-must-be-at-least-32-bytes-long"
        );
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpirationSeconds", 60L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenExpirationSeconds", 120L);

        user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("test@example.com");
        when(user.getRole()).thenReturn(Role.USER);
    }

    @Test
    @DisplayName("액세스 토큰과 리프레시 토큰의 용도를 구분한다")
    void distinguishesAccessAndRefreshTokens() {
        String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = jwtTokenProvider.createRefreshToken(user);

        assertThat(jwtTokenProvider.validateAccessToken(accessToken)).isTrue();
        assertThat(jwtTokenProvider.validateRefreshToken(accessToken)).isFalse();
        assertThat(jwtTokenProvider.validateRefreshToken(refreshToken)).isTrue();
        assertThat(jwtTokenProvider.validateAccessToken(refreshToken)).isFalse();
        assertThat(jwtTokenProvider.getUserId(refreshToken)).isEqualTo(1L);
        assertThat(jwtTokenProvider.getTokenId(refreshToken)).isNotBlank();
    }
}
