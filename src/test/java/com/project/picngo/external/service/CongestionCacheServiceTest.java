package com.project.picngo.external.service;

import com.project.picngo.external.CongestionApiClient;
import com.project.picngo.spot.service.AdministrativeCodeResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CongestionCacheServiceTest {

    @Mock
    private CongestionApiClient apiClient;

    @Mock
    private AdministrativeCodeResolver administrativeCodeResolver;

    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private CongestionCacheService congestionCacheService;

    @BeforeEach
    void setUp() {
        congestionCacheService = new CongestionCacheService(apiClient, administrativeCodeResolver, redisTemplate);
    }

    @Test
    @DisplayName("남산 팔각정 스팟은 접미어 '팔각정' 제거 및 '남산' 키워드를 통해 '남산공원(서울)'에 스마트 매칭된다")
    void smartMatch_namsanPalgakjeong() {
        // given
        Set<String> attractionNames = Set.of(
                "남산 케이블카",
                "남산골한옥마을",
                "남산공원(서울)",
                "덕수궁",
                "명동",
                "동대문역사문화공원"
        );

        // when
        String bestMatch = ReflectionTestUtils.invokeMethod(
                congestionCacheService,
                "findBestAttraction",
                "남산 팔각정",
                attractionNames
        );

        // then
        assertThat(bestMatch).isEqualTo("남산공원(서울)");
    }

    @Test
    @DisplayName("덕수궁 돌담길 스팟은 포함 매칭을 통해 '덕수궁'에 스마트 매칭된다")
    void smartMatch_deoksugungDoldamgil() {
        // given
        Set<String> attractionNames = Set.of(
                "덕수궁",
                "남대문시장",
                "서울시립미술관(서소문본관)"
        );

        // when
        String bestMatch = ReflectionTestUtils.invokeMethod(
                congestionCacheService,
                "findBestAttraction",
                "덕수궁 돌담길",
                attractionNames
        );

        // then
        assertThat(bestMatch).isEqualTo("덕수궁");
    }

    @Test
    @DisplayName("완전 일치 또는 괄호/특수문자만 다른 경우 정확히 매칭된다")
    void smartMatch_exactMatch() {
        // given
        Set<String> attractionNames = Set.of(
                "남산서울타워",
                "남산공원(서울)"
        );

        // when
        String match1 = ReflectionTestUtils.invokeMethod(
                congestionCacheService,
                "findBestAttraction",
                "남산서울타워",
                attractionNames
        );
        String match2 = ReflectionTestUtils.invokeMethod(
                congestionCacheService,
                "findBestAttraction",
                "남산공원",
                attractionNames
        );

        // then
        assertThat(match1).isEqualTo("남산서울타워");
        assertThat(match2).isEqualTo("남산공원(서울)");
    }

    @Test
    @DisplayName("전혀 관련 없는 골목길이나 개인 카페는 매칭되지 않고 null을 반환한다")
    void smartMatch_unknownSpot() {
        // given
        Set<String> attractionNames = Set.of(
                "남산 케이블카",
                "남산공원(서울)",
                "덕수궁"
        );

        // when
        String bestMatch = ReflectionTestUtils.invokeMethod(
                congestionCacheService,
                "findBestAttraction",
                "성수동 카페거리 어느 골목길",
                attractionNames
        );

        // then
        assertThat(bestMatch).isNull();
    }
}
