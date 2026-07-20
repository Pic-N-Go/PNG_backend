package com.project.picngo.course.service;

import com.project.picngo.external.DirectionsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteCacheService {

    private final StringRedisTemplate redisTemplate;
    private final DirectionsClient directionsClient;

    private static final String ROUTE_CACHE_KEY_PREFIX = "route:time:";
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    public Integer getTravelTimeMinutes(Double startLat, Double startLng, Double goalLat, Double goalLng) {
        String cacheKey = generateCacheKey(startLat, startLng, goalLat, goalLng);

        try {
            String cachedValue = redisTemplate.opsForValue().get(cacheKey);
            if (cachedValue != null) {
                return Integer.parseInt(cachedValue);
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 조회 실패, API 호출로 전환합니다. Key: {}", cacheKey, e);
        }

        try {
            Integer travelTime = directionsClient.getTravelTimeMinutes(startLat, startLng, goalLat, goalLng);
            
            if (travelTime != null) {
                try {
                    redisTemplate.opsForValue().set(cacheKey, String.valueOf(travelTime), CACHE_TTL);
                } catch (Exception e) {
                    log.warn("Redis 캐시 저장 실패. Key: {}", cacheKey, e);
                }
            }
            return travelTime;
        } catch (Exception e) {
            log.error("카카오 길찾기 API 호출 실패", e);
            // Fallback
            return 30;
        }
    }

    private String generateCacheKey(Double startLat, Double startLng, Double goalLat, Double goalLng) {
        // 소수점 3자리(약 100m) 단위로 반올림하여 캐시 히트율 증가
        return String.format(Locale.US, "%s%.3f_%.3f:%.3f_%.3f",
                ROUTE_CACHE_KEY_PREFIX, startLat, startLng, goalLat, goalLng);
    }
}
