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
 * 유사도 폴백(picngo.search.similar-fallback) - 오타 흡수용 마지막 단계.
 *
 * <p>앞의 두 단계가 모두 0건일 때만 돌아야 한다. 이 단계는 조각 하나만 겹쳐도 결과를
 * 내놓기 때문에, 순서가 어긋나면 정확히 찾을 수 있는 검색에까지 엉뚱한 결과가 섞인다.
 */
@ExtendWith(MockitoExtension.class)
class SpotServiceSimilarFallbackTest {

    @Mock
    private SpotRepository spotRepository;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private SpotService service(boolean normalize, boolean similar) {
        return new SpotService(
                spotRepository, null, null, null, null,
                meterRegistry,
                new SearchProperties(SearchEngine.FULLTEXT, normalize, similar, false, false),
                null
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
    @DisplayName("앞 두 단계가 모두 0건이면 따옴표 없는 검색어로 유사도 검색을 한다")
    void fallsBackToSimilaritySearch() {
        given(spotRepository.searchSpotsFullText(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(spotRepository.searchSpotsNormalized(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(spotRepository.searchSpotsSimilar(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        var response = service(true, true).searchSpots("갈산공줜", null, 0, 20, null);

        // 따옴표가 붙으면 구문 검색이 되어 오타를 흡수하지 못한다. 반드시 맨 문자열이어야 한다.
        verify(spotRepository).searchSpotsSimilar(eq("갈산공줜"), eq("APPROVED"), any());
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(stageCount("similar")).isEqualTo(1d);
    }

    @Test
    @DisplayName("1차에서 결과가 나오면 유사도 검색을 하지 않는다")
    void skipsWhenPrimaryHasResults() {
        given(spotRepository.searchSpotsFullText(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        service(true, true).searchSpots("갈산공원", null, 0, 20, null);

        verify(spotRepository, never()).searchSpotsSimilar(any(), any(), any());
        assertThat(stageCount("primary")).isEqualTo(1d);
    }

    @Test
    @DisplayName("띄어쓰기 폴백에서 결과가 나오면 유사도 검색까지 가지 않는다")
    void skipsWhenNormalizedStageSucceeds() {
        given(spotRepository.searchSpotsFullText(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(spotRepository.searchSpotsNormalized(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        service(true, true).searchSpots("갈 산공원", null, 0, 20, null);

        verify(spotRepository, never()).searchSpotsSimilar(any(), any(), any());
        assertThat(stageCount("normalized")).isEqualTo(1d);
    }

    @Test
    @DisplayName("유사도 검색어에서도 공백과 문장부호를 지운다 - 색인과 같은 규칙")
    void normalizesSimilarityKeyword() {
        given(spotRepository.searchSpotsFullText(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(spotRepository.searchSpotsSimilar(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        service(false, true).searchSpots("극락사(서 울)", null, 0, 20, null);

        verify(spotRepository).searchSpotsSimilar(eq("극락사서울"), eq("APPROVED"), any());
    }

    @Test
    @DisplayName("카테고리 필터가 있으면 유사도 검색도 카테고리 버전을 쓴다")
    void usesCategoryVariant() {
        given(spotRepository.searchSpotsFullTextByCategories(any(), any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(spotRepository.searchSpotsSimilarByCategories(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        service(false, true).searchSpots("갈산공줜", List.of("PARK"), 0, 20, null);

        verify(spotRepository).searchSpotsSimilarByCategories(
                eq("갈산공줜"), eq(List.of("PARK")), eq("APPROVED"), any());
    }

    @Test
    @DisplayName("유사도 폴백이 꺼져 있으면 세 번째 쿼리를 치지 않는다")
    void neverQueriesWhenDisabled() {
        given(spotRepository.searchSpotsFullText(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));

        service(false, false).searchSpots("갈산공줜", null, 0, 20, null);

        verify(spotRepository, never()).searchSpotsSimilar(any(), any(), any());
        assertThat(stageCount("none")).isEqualTo(1d);
    }

    @Test
    @DisplayName("유사도 검색도 0건이면 stage=none으로 기록한다")
    void recordsNoneWhenAllStagesEmpty() {
        given(spotRepository.searchSpotsFullText(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(spotRepository.searchSpotsSimilar(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));

        service(false, true).searchSpots("없는스팟", null, 0, 20, null);

        assertThat(stageCount("none")).isEqualTo(1d);
        assertThat(stageCount("similar")).isZero();
    }
}
