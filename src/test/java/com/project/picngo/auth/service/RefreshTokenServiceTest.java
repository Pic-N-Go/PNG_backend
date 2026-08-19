package com.project.picngo.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 사용자별 토큰 인덱스(auth:refresh:user:{id})를 다루는 집합 연산. */
    @Mock
    private SetOperations<String, String> setOperations;

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
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

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
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
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
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.getAndDelete("auth:refresh:token-id")).thenReturn(null);

        boolean consumed = refreshTokenService.consumeRefreshToken("refresh-token", 1L);

        assertThat(consumed).isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 리프레시 토큰은 거부한다")
    void rejectsRefreshTokenForDifferentUser() {
        when(jwtTokenProvider.getTokenId("refresh-token")).thenReturn("token-id");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.getAndDelete("auth:refresh:token-id")).thenReturn("2");

        boolean consumed = refreshTokenService.consumeRefreshToken("refresh-token", 1L);

        assertThat(consumed).isFalse();
    }

    @Test
    @DisplayName("사용자의 리프레시 토큰을 전부 폐기한다")
    void revokesAllTokensOfUser() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("auth:refresh:user:1")).thenReturn(Set.of("t1", "t2"));

        refreshTokenService.revokeAllByUserId(1L);

        // 토큰 키는 tokenId 기준이라, 인덱스에서 목록을 얻어야 지울 수 있다.
        ArgumentCaptor<Collection<String>> keys = ArgumentCaptor.forClass(Collection.class);
        verify(redisTemplate).delete(keys.capture());
        assertThat(keys.getValue()).containsExactlyInAnyOrder("auth:refresh:t1", "auth:refresh:t2");
        verify(redisTemplate).delete("auth:refresh:user:1");
    }

    @Test
    @DisplayName("폐기할 토큰이 없어도 인덱스는 지운다")
    void revokeWithoutTokensStillClearsIndex() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("auth:refresh:user:1")).thenReturn(Set.of());

        refreshTokenService.revokeAllByUserId(1L);

        verify(redisTemplate).delete("auth:refresh:user:1");
    }
}
