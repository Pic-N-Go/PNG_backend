package com.project.picngo.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    public void saveRefreshToken(String refreshToken, Long userId){
        String tokenId = jwtTokenProvider.getTokenId(refreshToken);

        redisTemplate.opsForValue().set(key(tokenId), userId.toString(), Duration.ofSeconds(jwtTokenProvider.getRefreshTokenExpirationSeconds()));
    }

    public boolean consumeRefreshToken(String refreshToken, Long userId){
        String tokenId = jwtTokenProvider.getTokenId(refreshToken);

        String storedUserId = redisTemplate.opsForValue().getAndDelete(key(tokenId));

        return userId.toString().equals(storedUserId);
    }

    private String key(String tokenId){
        return KEY_PREFIX + tokenId;
    }

}
