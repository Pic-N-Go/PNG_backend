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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 편집거리 오타 폴백(picngo.search.fuzzy-fallback).
 *
 * <p>앞의 유사도 검색(ngram)이 못 잡는 짧은 검색어의 오타를 받아내는 단계다.
 * 실제로 DB에서 MATCH 점수가 0이었던 '헙재'·'오셜록'이 여기서는 잡혀야 한다.
 */
@ExtendWith(MockitoExtension.class)
class SpotServiceFuzzyFallbackTest {

    @Mock
    private SpotRepository spotRepository;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private SpotService service(boolean similar, boolean fuzzy) {
        return new SpotService(
                spotRepository, null, null, null, null,
                meterRegistry,
                new SearchProperties(SearchEngine.FULLTEXT, false, similar, fuzzy, false),
                null
        );
    }

    private Spot spot(Long id, String name) {
        Spot spot = Spot.builder()
                .name(name)
                .address("제주특별자치도 제주시 한림읍")
                .latitude(33.4)
                .longitude(126.2)
                .categories(Set.of(SpotCategory.BEACH))
                .status(SpotStatus.APPROVED)
                .build();
        ReflectionTestUtils.setField(spot, "id", id);
        return spot;
    }

    private SpotRepository.FuzzyCandidate candidate(Long id, String name, String address) {
        return new SpotRepository.FuzzyCandidate() {
            @Override
            public Long getId() { return id; }
            @Override
            public String getName() { return name; }
            @Override
            public String getAddress() { return address; }
        };
    }

    private double stageCount(String stage) {
        var counter = meterRegistry.find("spot.search.stage").tag("stage", stage).counter();
        return counter == null ? 0d : counter.count();
    }

    private void primaryReturnsNothing() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
    }

    @Test
    @DisplayName("두 글자 검색어의 오타를 잡는다 - '헙재'로 협재해수욕장")
    void findsTwoCharacterTypo() {
        primaryReturnsNothing();
        given(spotRepository.findFuzzyCandidates(SpotStatus.APPROVED)).willReturn(List.of(
                candidate(5L, "협재해수욕장", "제주특별자치도 제주시 한림읍")
        ));
        given(spotRepository.findByIdIn(List.of(5L)))
                .willReturn(List.of(spot(5L, "협재해수욕장")));

        var response = service(false, true).searchSpots("헙재", null, 0, 20, null);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).name()).isEqualTo("협재해수욕장");
        assertThat(stageCount("fuzzy")).isEqualTo(1d);
    }

    @Test
    @DisplayName("가운데 글자 오타를 잡는다 - '오셜록'으로 오설록 티 뮤지엄")
    void findsMiddleCharacterTypo() {
        primaryReturnsNothing();
        given(spotRepository.findFuzzyCandidates(SpotStatus.APPROVED)).willReturn(List.of(
                candidate(3L, "오설록 티 뮤지엄", "제주특별자치도 서귀포시 안덕면")
        ));
        given(spotRepository.findByIdIn(List.of(3L)))
                .willReturn(List.of(spot(3L, "오설록 티 뮤지엄")));

        var response = service(false, true).searchSpots("오셜록", null, 0, 20, null);

        assertThat(response.getContent().get(0).name()).isEqualTo("오설록 티 뮤지엄");
        assertThat(stageCount("fuzzy")).isEqualTo(1d);
    }

    @Test
    @DisplayName("덜 틀린 것부터 보여준다")
    void ranksByDistanceAscending() {
        primaryReturnsNothing();
        given(spotRepository.findFuzzyCandidates(SpotStatus.APPROVED)).willReturn(List.of(
                candidate(1L, "협제해변", "제주"),      // '협재'와 1글자 차이
                candidate(2L, "협재해수욕장", "제주")   // '협재'와 정확히 일치
        ));
        given(spotRepository.findByIdIn(List.of(2L, 1L))).willReturn(List.of(
                spot(1L, "협제해변"), spot(2L, "협재해수욕장")
        ));

        var response = service(false, true).searchSpots("협재", null, 0, 20, null);

        assertThat(response.getContent()).extracting("name")
                .containsExactly("협재해수욕장", "협제해변");
    }

    @Test
    @DisplayName("전혀 다른 이름은 후보에 넣지 않는다")
    void excludesUnrelatedSpots() {
        primaryReturnsNothing();
        given(spotRepository.findFuzzyCandidates(SpotStatus.APPROVED)).willReturn(List.of(
                candidate(9L, "한라산", "제주특별자치도 서귀포시")
        ));

        var response = service(false, true).searchSpots("헙재", null, 0, 20, null);

        assertThat(response.getTotalElements()).isZero();
        assertThat(stageCount("none")).isEqualTo(1d);
        assertThat(stageCount("fuzzy")).isZero();
    }

    @Test
    @DisplayName("한 글자 검색어에는 돌지 않는다 - 아무 스팟이나 걸린다")
    void skipsSingleCharacterKeyword() {
        primaryReturnsNothing();

        service(false, true).searchSpots("협", null, 0, 20, null);

        verify(spotRepository, never()).findFuzzyCandidates(any());
        assertThat(stageCount("none")).isEqualTo(1d);
    }

    @Test
    @DisplayName("1차에서 결과가 나오면 편집거리 검색을 하지 않는다")
    void skipsWhenPrimaryHasResults() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot(5L, "협재해수욕장"))));

        service(false, true).searchSpots("협재", null, 0, 20, null);

        verify(spotRepository, never()).findFuzzyCandidates(any());
        assertThat(stageCount("primary")).isEqualTo(1d);
    }

    @Test
    @DisplayName("유사도 단계에서 결과가 나오면 편집거리까지 가지 않는다")
    void skipsWhenSimilarStageSucceeds() {
        primaryReturnsNothing();
        given(spotRepository.searchSpotsSimilar(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot(5L, "협재해수욕장"))));

        service(true, true).searchSpots("협재해수욕쟝", null, 0, 20, null);

        verify(spotRepository, never()).findFuzzyCandidates(any());
        assertThat(stageCount("similar")).isEqualTo(1d);
    }

    @Test
    @DisplayName("폴백이 꺼져 있으면 후보를 조회하지 않는다")
    void neverQueriesWhenDisabled() {
        primaryReturnsNothing();

        service(false, false).searchSpots("헙재", null, 0, 20, null);

        verify(spotRepository, never()).findFuzzyCandidates(any());
        assertThat(stageCount("none")).isEqualTo(1d);
    }

    @Test
    @DisplayName("카테고리 필터가 있으면 카테고리 버전으로 후보를 좁힌다")
    void usesCategoryVariant() {
        given(spotRepository.searchSpotsFullTextByCategories(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(spotRepository.findFuzzyCandidatesByCategories(List.of(SpotCategory.BEACH), SpotStatus.APPROVED))
                .willReturn(List.of(candidate(5L, "협재해수욕장", "제주")));
        given(spotRepository.findByIdIn(List.of(5L)))
                .willReturn(List.of(spot(5L, "협재해수욕장")));

        var response = service(false, true).searchSpots("헙재", List.of("BEACH"), 0, 20, null);

        assertThat(response.getTotalElements()).isEqualTo(1);
        verify(spotRepository, never()).findFuzzyCandidates(any());
    }
}
