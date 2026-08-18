package com.project.picngo.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    @DisplayName("리프레시 토큰 식별자를 만료 시간과 함께 Redis에 저장한다")
    void savesRefreshTokenWithExpiration() {
        when(jwtTokenProvider.getTokenId("refresh-token")).thenReturn("token-id");
        when(jwtTokenProvider.getRefreshTokenExpirationSeconds()).thenReturn(120L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        refreshTokenService.saveRefreshToken("refresh-token", 1L);

        verify(valueOperations).set(
                "auth:refresh:token-id",
                "1",
                Duration.ofSeconds(120L)
        );
    }

    @Test
    @DisplayName("등록된 리프레시 토큰을 조회와 동시에 삭제한다")
    void consumesRegisteredRefreshToken() {
        when(jwtTokenProvider.getTokenId("refresh-token")).thenReturn("token-id");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("auth:refresh:token-id")).thenReturn("1");

        boolean consumed = refreshTokenService.consumeRefreshToken("refresh-token", 1L);

        assertThat(consumed).isTrue();
        verify(valueOperations).getAndDelete("auth:refresh:token-id");
    }

    @Test
    @DisplayName("Redis에 없는 리프레시 토큰은 거부한다")
    void rejectsUnregisteredRefreshToken() {
        when(jwtTokenProvider.getTokenId("refresh-token")).thenReturn("token-id");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("auth:refresh:token-id")).thenReturn(null);

        boolean consumed = refreshTokenService.consumeRefreshToken("refresh-token", 1L);

        assertThat(consumed).isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 리프레시 토큰은 거부한다")
    void rejectsRefreshTokenForDifferentUser() {
        when(jwtTokenProvider.getTokenId("refresh-token")).thenReturn("token-id");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("auth:refresh:token-id")).thenReturn("2");

        boolean consumed = refreshTokenService.consumeRefreshToken("refresh-token", 1L);

        assertThat(consumed).isFalse();
    }
}
