package com.project.picngo.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "auth:refresh:";
    /**
     * 사용자별 토큰 인덱스. 토큰 키만으로는 "이 사용자의 토큰 전부"를 찾을 수 없어
     * (키가 tokenId 기준이다) 비밀번호 변경 시 일괄 폐기를 할 수 없다.
     */
    private static final String USER_INDEX_PREFIX = "auth:refresh:user:";

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    public void saveRefreshToken(String refreshToken, Long userId){
        String tokenId = jwtTokenProvider.getTokenId(refreshToken);
        Duration ttl = Duration.ofSeconds(jwtTokenProvider.getRefreshTokenExpirationSeconds());

        redisTemplate.opsForValue().set(key(tokenId), userId.toString(), ttl);
        redisTemplate.opsForSet().add(userKey(userId), tokenId);
        // 인덱스도 함께 만료시킨다 — 안 걸면 토큰이 다 만료된 뒤에도 집합만 영원히 남는다.
        redisTemplate.expire(userKey(userId), ttl);
    }

    public boolean consumeRefreshToken(String refreshToken, Long userId){
        String tokenId = jwtTokenProvider.getTokenId(refreshToken);

        String storedUserId = redisTemplate.opsForValue().getAndDelete(key(tokenId));
        redisTemplate.opsForSet().remove(userKey(userId), tokenId);

        return userId.toString().equals(storedUserId);
    }

    /**
     * 이 사용자의 리프레시 토큰을 전부 폐기한다. 비밀번호가 바뀌면 이전 자격으로 만들어진
     * 세션은 끊겨야 한다 — 비밀번호를 바꾸는 이유가 보통 "남이 쓰고 있을지도 모른다"라서다.
     */
    public void revokeAllByUserId(Long userId){
        Set<String> tokenIds = redisTemplate.opsForSet().members(userKey(userId));
        if (tokenIds != null && !tokenIds.isEmpty()) {
            redisTemplate.delete(tokenIds.stream().map(this::key).toList());
        }
        redisTemplate.delete(userKey(userId));
    }

    private String key(String tokenId){
        return KEY_PREFIX + tokenId;
    }

    private String userKey(Long userId){
        return USER_INDEX_PREFIX + userId;
    }

}
