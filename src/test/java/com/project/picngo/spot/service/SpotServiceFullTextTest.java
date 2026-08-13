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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * picngo.search.engine=FULLTEXT 일 때의 분기.
 *
 * <p>여기서 고정하는 것들은 전부 "틀려도 예외가 안 나고 결과만 조용히 달라지는" 성질이다.
 * 정렬이 어긋나거나 LIKE 쿼리로 새면 두 방식의 비교 자체가 무의미해진다.
 */
@ExtendWith(MockitoExtension.class)
class SpotServiceFullTextTest {

    @Mock
    private SpotRepository spotRepository;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Spy
    private SearchProperties searchProperties = new SearchProperties(SearchEngine.FULLTEXT, false, false, false);

    @InjectMocks
    private SpotService spotService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private Spot spot() {
        return Spot.builder()
                .name("한라산")
                .address("제주특별자치도 서귀포시")
                .latitude(33.36)
                .longitude(126.53)
                .categories(Set.of(SpotCategory.MOUNTAIN))
                .status(SpotStatus.APPROVED)
                .build();
    }

    @Test
    @DisplayName("FULLTEXT 엔진이면 LIKE 쿼리 대신 전문검색 쿼리를 호출한다")
    void usesFullTextQuery() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        spotService.searchSpots("한라산", null, 0, 20);

        verify(spotRepository).searchSpotsFullText(eq("\"한라산\""), eq("APPROVED"), any());
        verify(spotRepository, never()).searchSpots(any(), any(), any());
    }

    @Test
    @DisplayName("네이티브 쿼리에 Sort를 얹지 않는다 - ORDER BY가 두 번 붙어 쿼리가 깨진다")
    void passesUnsortedPageable() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        spotService.searchSpots("한라산", null, 0, 20);

        verify(spotRepository).searchSpotsFullText(any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().isSorted()).isFalse();
    }

    @Test
    @DisplayName("카테고리 필터가 있으면 enum이 아니라 이름 문자열로 넘긴다 - 네이티브 쿼리라 바인딩이 모호하다")
    void passesCategoryNamesAsStrings() {
        given(spotRepository.searchSpotsFullTextByCategories(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        spotService.searchSpots("한라산", List.of("MOUNTAIN"), 0, 20);

        verify(spotRepository).searchSpotsFullTextByCategories(
                eq("\"한라산\""), eq(List.of("MOUNTAIN")), eq("APPROVED"), any());
    }

    @Test
    @DisplayName("전문검색식으로 바꿀 수 없는 검색어는 DB를 치지 않고 빈 결과를 준다")
    void returnsEmptyWithoutQueryingWhenPhraseIsUnbuildable() {
        var response = spotService.searchSpots("+++", null, 0, 20);

        assertThat(response.getTotalElements()).isZero();
        verify(spotRepository, never()).searchSpotsFullText(any(), any(), any());
    }

    @Test
    @DisplayName("지표에 engine=FULLTEXT 태그가 붙는다 - 공통 태그와 대소문자가 같아야 한다")
    void tagsMetricsWithEngine() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        spotService.searchSpots("한라산", null, 0, 20);

        var timer = meterRegistry.find("spot.search.duration")
                .tag("type", "keyword")
                .tag("phase", "query")
                .tag("engine", "FULLTEXT")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }
}
