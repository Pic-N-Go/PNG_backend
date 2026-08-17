package com.project.picngo.spot.service;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.external.EmbeddingClient;
import com.project.picngo.spot.config.SearchEngine;
import com.project.picngo.spot.config.SearchProperties;
import com.project.picngo.spot.domain.EmbeddingVector;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 의미 검색 폴백(picngo.search.semantic-fallback) - 앞 세 단계가 모두 0건일 때만 도는
 * 마지막 단계. 브루트포스로 후보 임베딩 전부와 코사인 유사도를 비교해 가장 가까운 순으로 돌려준다.
 */
@ExtendWith(MockitoExtension.class)
class SpotServiceSemanticFallbackTest {

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private EmbeddingClient embeddingClient;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private SpotService service(boolean semantic) {
        return new SpotService(
                spotRepository, null, null, null, null,
                meterRegistry,
                new SearchProperties(SearchEngine.FULLTEXT, false, false, false, semantic),
                embeddingClient
        );
    }

    private Spot spot(Long id, String name) {
        Spot spot = Spot.builder()
                .name(name)
                .address("서울특별시 종로구")
                .latitude(37.5)
                .longitude(127.0)
                .categories(Set.of(SpotCategory.MOUNTAIN))
                .status(SpotStatus.APPROVED)
                .build();
        ReflectionTestUtils.setField(spot, "id", id);
        return spot;
    }

    private SpotRepository.EmbeddingCandidate candidate(Long id, float[] vector) {
        byte[] encoded = EmbeddingVector.encode(vector);
        return new SpotRepository.EmbeddingCandidate() {
            @Override
            public Long getId() { return id; }
            @Override
            public byte[] getEmbedding() { return encoded; }
        };
    }

    private double stageCount(String stage) {
        var counter = meterRegistry.find("spot.search.stage").tag("stage", stage).counter();
        return counter == null ? 0d : counter.count();
    }

    @Test
    @DisplayName("앞 세 단계가 모두 0건이면 임베딩 코사인 유사도로 검색한다")
    void fallsBackToSemanticSearch() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(embeddingClient.embed("해질녘 걷기 좋은 곳"))
                .willReturn(Optional.of(new float[]{1f, 0f}));
        given(spotRepository.findEmbeddingCandidates(SpotStatus.APPROVED))
                .willReturn(List.of(candidate(1L, new float[]{1f, 0f})));
        given(spotRepository.findByIdIn(List.of(1L)))
                .willReturn(List.of(spot(1L, "노을공원")));

        var response = service(true).searchSpots("해질녘 걷기 좋은 곳", null, 0, 20, null);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).name()).isEqualTo("노을공원");
        assertThat(stageCount("semantic")).isEqualTo(1d);
    }

    @Test
    @DisplayName("후보를 코사인 유사도 내림차순으로 정렬해서 돌려준다")
    void ranksCandidatesByCosineSimilarityDescending() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(embeddingClient.embed(any())).willReturn(Optional.of(new float[]{1f, 0f}));
        given(spotRepository.findEmbeddingCandidates(SpotStatus.APPROVED)).willReturn(List.of(
                candidate(1L, new float[]{0f, 1f}),   // 직교 → 유사도 0
                candidate(2L, new float[]{1f, 0f}),   // 동일 → 유사도 1
                candidate(3L, new float[]{0.7f, 0.7f}) // 중간
        ));
        given(spotRepository.findByIdIn(List.of(2L, 3L, 1L))).willReturn(List.of(
                spot(1L, "3위"), spot(2L, "1위"), spot(3L, "2위")
        ));

        var response = service(true).searchSpots("아무 검색어", null, 0, 20, null);

        assertThat(response.getContent()).extracting("name")
                .containsExactly("1위", "2위", "3위");
    }

    @Test
    @DisplayName("1차에서 결과가 나오면 임베딩 검색을 하지 않는다")
    void skipsWhenPrimaryHasResults() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(spot(1L, "갈산공원"))));

        service(true).searchSpots("갈산공원", null, 0, 20, null);

        verify(embeddingClient, never()).embed(any());
    }

    @Test
    @DisplayName("의미 검색 폴백이 꺼져 있으면 임베딩 API를 부르지 않는다")
    void neverCallsEmbeddingWhenDisabled() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));

        service(false).searchSpots("없는스팟", null, 0, 20, null);

        verify(embeddingClient, never()).embed(any());
        assertThat(stageCount("none")).isEqualTo(1d);
    }

    @Test
    @DisplayName("임베딩 클라이언트가 빈 값을 돌려주면 stage=none으로 기록한다")
    void recordsNoneWhenEmbeddingClientReturnsEmpty() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(embeddingClient.embed(any())).willReturn(Optional.empty());

        service(true).searchSpots("없는스팟", null, 0, 20, null);

        verify(spotRepository, never()).findEmbeddingCandidates(any());
        assertThat(stageCount("none")).isEqualTo(1d);
        assertThat(stageCount("semantic")).isZero();
    }

    @Test
    @DisplayName("후보 임베딩이 없으면 stage=none으로 기록한다")
    void recordsNoneWhenNoCandidates() {
        given(spotRepository.searchSpotsFullText(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(embeddingClient.embed(any())).willReturn(Optional.of(new float[]{1f, 0f}));
        given(spotRepository.findEmbeddingCandidates(SpotStatus.APPROVED)).willReturn(List.of());

        service(true).searchSpots("없는스팟", null, 0, 20, null);

        assertThat(stageCount("none")).isEqualTo(1d);
    }
}
