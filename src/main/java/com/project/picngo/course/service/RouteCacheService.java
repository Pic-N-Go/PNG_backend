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
                log.info("캐시 히트(Cache Hit) 발생: 카카오 길찾기 - {}", cacheKey);
                return Integer.parseInt(cachedValue);
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 조회 실패, API 호출로 전환합니다. Key: {}", cacheKey, e);
        }

        Integer travelTime = null;
        try {
            travelTime = directionsClient.getTravelTimeMinutes(startLat, startLng, goalLat, goalLng);
        } catch (Exception e) {
            log.error("카카오 길찾기 API 호출 실패", e);
        }

        if (travelTime == null) {
            travelTime = calculateFallbackTime(startLat, startLng, goalLat, goalLng);
        }

        try {
            redisTemplate.opsForValue().set(cacheKey, String.valueOf(travelTime), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis 캐시 저장 실패. Key: {}", cacheKey, e);
        }

        return travelTime;
    }

    private Integer calculateFallbackTime(Double startLat, Double startLng, Double goalLat, Double goalLng) {
        if (startLat == null || startLng == null || goalLat == null || goalLng == null) {
            return 30;
        }
        
        int R = 6371; // 지구의 반지름 (km)
        double dLat = Math.toRadians(goalLat - startLat);
        double dLon = Math.toRadians(goalLng - startLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(goalLat)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanceKm = R * c;
        
        // 제주도 평균 이동 속도 약 30km/h (0.5km/min) 로 가정
        // 최소 이동 시간 5분 보장
        int minutes = (int) Math.round(distanceKm * 2);
        return Math.max(5, minutes);
    }

    private String generateCacheKey(Double startLat, Double startLng, Double goalLat, Double goalLng) {
        // 소수점 3자리(약 100m) 단위로 반올림하여 캐시 히트율 증가
        return String.format(Locale.US, "%s%.3f_%.3f:%.3f_%.3f",
                ROUTE_CACHE_KEY_PREFIX, startLat, startLng, goalLat, goalLng);
    }
}
