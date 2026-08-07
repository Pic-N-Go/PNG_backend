package com.project.picngo.course.service;

import com.project.picngo.external.DirectionsClient;
import com.project.picngo.external.dto.DirectionsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private DirectionsClient directionsClient;

    @InjectMocks
    private RouteCacheService routeCacheService;

    @Test
    @DisplayName("유효 범위를 벗어난 0.0 좌표는 Fallback 이동시간 계산 시 null 처리 (케이스 C 지원)")
    void testCalculateFallbackTime_InvalidCoordinates() {
        Integer fallback = routeCacheService.calculateFallbackTime(0.0, 0.0, 33.4, 126.5);
        assertThat(fallback).isNull();
    }

    @Test
    @DisplayName("우회율 1.3배 및 구간별 누적 속도 모델 기반 Fallback 이동시간 정상 계산")
    void testCalculateFallbackTime_ValidKoreaCoordinates() {
        // 서울 -> 부산 근처 (직선거리 약 325km -> 도로 우회 422km -> 고속도로 적용 약 310분)
        Integer fallback = routeCacheService.calculateFallbackTime(37.5665, 126.9780, 35.1796, 129.0756);
        assertThat(fallback).isNotNull();
        assertThat(fallback).isBetween(280, 360); // 기존 650분 폭탄 오차 해소!
    }

    @Test
    @DisplayName("길찾기 API 실패 시 Redis 장기 캐시에 저장하지 않고 Fallback 값만 반환 (캐시 오염 100% 방지)")
    void testGetTravelTimeMinutes_ApiFailure_DoesNotCacheInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(directionsClient.getTravelInfo(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new DirectionsResponse(null, null, 103)); // 비도로 실패

        Integer result = routeCacheService.getTravelTimeMinutes(33.4, 126.5, 33.45, 126.55);

        assertThat(result).isNotNull();
        // Redis opsForValue().set 이 절대 호출되지 않았는지 검증!
        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }
}
