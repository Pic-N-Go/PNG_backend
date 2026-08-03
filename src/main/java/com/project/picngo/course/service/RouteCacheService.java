package com.project.picngo.course.service;

import com.project.picngo.external.DirectionsClient;
import com.project.picngo.external.dto.DirectionsResponse;
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
        DirectionsResponse response = getTravelInfoWithCache(startLat, startLng, goalLat, goalLng);
        if (response != null && response.travelTimeMinutes() != null) {
            return response.travelTimeMinutes();
        }
        return calculateFallbackTime(startLat, startLng, goalLat, goalLng);
    }

    public DirectionsResponse getTravelInfoWithCache(Double startLat, Double startLng, Double goalLat, Double goalLng) {
        String cacheKey = generateCacheKey(startLat, startLng, goalLat, goalLng);

        try {
            String cachedValue = redisTemplate.opsForValue().get(cacheKey);
            if (cachedValue != null) {
                log.info("캐시 히트(Cache Hit) 발생: 카카오 길찾기 - {}", cacheKey);
                return new DirectionsResponse(Integer.parseInt(cachedValue), null, 0);
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 조회 실패, API 호출로 전환합니다. Key: {}", cacheKey, e);
        }

        DirectionsResponse response = null;
        try {
            response = directionsClient.getTravelInfo(startLat, startLng, goalLat, goalLng);
        } catch (Exception e) {
            log.error("카카오 길찾기 API 호출 실패", e);
        }

        if (response != null && response.travelTimeMinutes() != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, String.valueOf(response.travelTimeMinutes()), CACHE_TTL);
            } catch (Exception e) {
                log.warn("Redis 캐시 저장 실패. Key: {}", cacheKey, e);
            }
        }

        return response;
    }

    public DirectionsResponse getTravelInfo(Double startLat, Double startLng, Double goalLat, Double goalLng) {
        try {
            return directionsClient.getTravelInfo(startLat, startLng, goalLat, goalLng);
        } catch (Exception e) {
            log.warn("카카오 길찾기 상세 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 대한민국 유효 좌표 검증 (위도 33.0 ~ 39.0, 경도 124.0 ~ 132.0)
     */
    public boolean isValidKoreaCoordinate(Double lat, Double lng) {
        return lat != null && lng != null && lat >= 33.0 && lat <= 39.0 && lng >= 124.0 && lng <= 132.0;
    }

    /**
     * 우회율 1.3배 및 구간별 누적 소진 속도(Speed Bucket) 모델 적용 Fallback 계산
     * - 0~5km: 22km/h (시내)
     * - 5~20km: 40km/h (국도)
     * - 20~50km: 55km/h (도시고속도로)
     * - 50km 초과: 85km/h (고속도로)
     */
    public Integer calculateFallbackTime(Double startLat, Double startLng, Double goalLat, Double goalLng) {
        if (!isValidKoreaCoordinate(startLat, startLng) || !isValidKoreaCoordinate(goalLat, goalLng)) {
            log.warn("⚠️ 유효 범위를 벗어난 좌표로 Fallback 이동시간 산출 불가 (위도/경도 확인 필요)");
            return null; // 아프리카 기니만 등 0.0 유령 좌표는 null 처리 (케이스 C: 도서/산출 불가)
        }

        double R = 6371.0; // 지구 반지름 (km)
        double dLat = Math.toRadians(goalLat - startLat);
        double dLon = Math.toRadians(goalLng - startLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(goalLat)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double straightDistanceKm = R * c;

        // 대한민국 평균 도로 우회율 1.3배 반영
        double estimatedRoadDistanceKm = straightDistanceKm * 1.3;

        // 구간별 누적 소진 속도 모델 적용
        double remainingKm = estimatedRoadDistanceKm;
        double totalHours = 0.0;

        // 1. 시내 구간 (0~5km): 22 km/h
        double segment1 = Math.min(remainingKm, 5.0);
        totalHours += segment1 / 22.0;
        remainingKm -= segment1;

        // 2. 국도 구간 (5~20km): 40 km/h
        if (remainingKm > 0) {
            double segment2 = Math.min(remainingKm, 15.0);
            totalHours += segment2 / 40.0;
            remainingKm -= segment2;
        }

        // 3. 도시고속 구간 (20~50km): 55 km/h
        if (remainingKm > 0) {
            double segment3 = Math.min(remainingKm, 30.0);
            totalHours += segment3 / 55.0;
            remainingKm -= segment3;
        }

        // 4. 장거리 고속도로 구간 (50km 초과): 85 km/h
        if (remainingKm > 0) {
            totalHours += remainingKm / 85.0;
        }

        int minutes = (int) Math.round(totalHours * 60.0);
        return Math.max(3, minutes);
    }

    /**
     * 주차장(보정 좌표)에서 스팟 원본 좌표까지의 등산/도보 소요시간(분) 자체 추정 (케이스 B 지원)
     * - 산악 탐방로 우회율: 1.5배
     * - 등산/도보 평균 속도: 시속 3.6 km (분당 60m)
     */
    public Integer calculateWalkingMinutes(Double parkLat, Double parkLng, Double spotLat, Double spotLng) {
        if (!isValidKoreaCoordinate(parkLat, parkLng) || !isValidKoreaCoordinate(spotLat, spotLng)) {
            return null;
        }
        double R = 6371000.0; // m
        double dLat = Math.toRadians(spotLat - parkLat);
        double dLon = Math.toRadians(spotLng - parkLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(parkLat)) * Math.cos(Math.toRadians(spotLat)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double straightMeters = R * c;

        double walkingMeters = straightMeters * 1.5;
        int walkingMinutes = (int) Math.round(walkingMeters / 60.0); // 분당 60m
        return Math.max(1, walkingMinutes);
    }

    private String generateCacheKey(Double startLat, Double startLng, Double goalLat, Double goalLng) {
        // 소수점 3자리(약 100m) 단위로 반올림하여 캐시 히트율 증가
        return String.format(Locale.US, "%s%.3f_%.3f:%.3f_%.3f",
                ROUTE_CACHE_KEY_PREFIX, startLat, startLng, goalLat, goalLng);
    }
}
