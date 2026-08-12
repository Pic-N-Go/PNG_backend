package com.project.picngo.spot.service;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.config.SearchEngine;
import com.project.picngo.spot.config.SearchProperties;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.repository.SpotRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 띄어쓰기 무시 폴백(picngo.search.normalize-fallback).
 *
 * <p>폴백은 "앞 단계가 0건일 때만" 도는 것이 핵심이다. 이 조건이 깨지면 정상 검색마다
 * 쿼리가 한 번씩 더 나가는데, 결과는 똑같이 나와서 눈으로는 알 수 없고 비용만 두 배가 된다.
 *
 * <p>설정값이 다른 SpotService 두 개를 비교해야 해서 @InjectMocks 대신 직접 생성한다.
 * (@Nested 안의 @InjectMocks는 바깥 클래스의 @Spy 필드를 주입받지 못한다.)
 */
@ExtendWith(MockitoExtension.class)
class SpotServiceNormalizeFallbackTest {

    @Mock
    private SpotRepository spotRepository;

    // JUnit 5는 테스트 메서드마다 인스턴스를 새로 만들므로 지표도 매번 초기화된다.
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    /** 검색 경로만 검증하므로 쓰이지 않는 리포지토리는 null로 둔다. */
    private SpotService serviceWithFallback(boolean enabled) {
        return new SpotService(
                spotRepository, null, null, null, null,
                meterRegistry,
                new SearchProperties(SearchEngine.FULLTEXT, enabled, false)
        );
    }

    private Spot spot() {
        return Spot.builder()
                .name("갈산공원")
                .address("서울특별시 양천구 신정동")
                .latitude(37.5)
                .longitude(126.8)
                .categories(Set.of(SpotCategory.PARK))
                .status(SpotStatus.APPROVED)
                .build();
    }

    private double stageCount(String stage) {
        var counter = meterRegistry.find("spot.search.stage").tag("stage", stage).counter();
        return counter == null ? 0d : counter.count();
    }

    @Test
    @DisplayName("1차 검색에 결과가 있으면 폴백 쿼리를 치지 않는다")
    void skipsFallbackWhenPrimaryHasResults() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        serviceWithFallback(true).searchSpots("갈산공원", null, 0, 20);

        verify(spotRepository, never()).searchSpotsNormalized(any(), any(), any());
        assertThat(stageCount("primary")).isEqualTo(1d);
    }

    @Test
    @DisplayName("1차가 0건이면 공백을 지운 검색어로 폴백 쿼리를 친다")
    void fallsBackWithSpacelessKeyword() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(spotRepository.searchSpotsNormalized(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        var response = serviceWithFallback(true).searchSpots("갈 산공원", null, 0, 20);

        verify(spotRepository).searchSpotsNormalized(eq("\"갈산공원\""), eq("APPROVED"), any());
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(stageCount("normalized")).isEqualTo(1d);
    }

    @Test
    @DisplayName("이미 붙여 쓴 검색어도 그대로 폴백에 넘어간다")
    void handlesAlreadySpacelessKeyword() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(spotRepository.searchSpotsNormalized(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        serviceWithFallback(true).searchSpots("강남마이스관광특구", null, 0, 20);

        verify(spotRepository).searchSpotsNormalized(eq("\"강남마이스관광특구\""), eq("APPROVED"), any());
    }

    @Test
    @DisplayName("카테고리 필터가 있으면 폴백도 카테고리 버전을 쓴다")
    void fallsBackWithCategories() {
        given(spotRepository.searchSpotsFullTextByCategories(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(spotRepository.searchSpotsNormalizedByCategories(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        serviceWithFallback(true).searchSpots("갈 산공원", List.of("PARK"), 0, 20);

        verify(spotRepository).searchSpotsNormalizedByCategories(
                eq("\"갈산공원\""), eq(List.of("PARK")), eq("APPROVED"), any());
    }

    @Test
    @DisplayName("두 단계 모두 0건이면 stage=none으로 기록한다")
    void recordsNoneWhenAllStagesEmpty() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(spotRepository.searchSpotsNormalized(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));

        var response = serviceWithFallback(true).searchSpots("없는스팟", null, 0, 20);

        assertThat(response.getTotalElements()).isZero();
        assertThat(stageCount("none")).isEqualTo(1d);
        assertThat(stageCount("normalized")).isZero();
    }

    @Test
    @DisplayName("폴백이 꺼져 있으면 1차가 0건이어도 폴백 쿼리를 치지 않는다")
    void neverQueriesNormalizedWhenDisabled() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));

        serviceWithFallback(false).searchSpots("갈 산공원", null, 0, 20);

        verify(spotRepository, never()).searchSpotsNormalized(any(), any(), any());
        assertThat(stageCount("none")).isEqualTo(1d);
    }
}
