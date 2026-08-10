package com.project.picngo.spot.service;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.dto.MapBoundsRequest;
import com.project.picngo.spot.repository.SpotRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * 검색 계측 지표가 그라파나 대시보드가 기대하는 이름/태그로 나가는지 고정한다.
 * 지표 이름이나 태그 키가 조용히 바뀌면 앱은 멀쩡히 돌지만 대시보드 패널만 비어서
 * 한참 뒤에야 발견된다 - 그 실패를 여기서 잡는다.
 */
@ExtendWith(MockitoExtension.class)
class SpotServiceSearchMetricsTest {

    @Mock
    private SpotRepository spotRepository;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private SpotService spotService;

    private Spot spot() {
        return Spot.builder()
                .name("테스트 스팟")
                .address("서울특별시 종로구")
                .latitude(37.5)
                .longitude(127.0)
                .categories(Set.of(SpotCategory.MOUNTAIN))
                .status(SpotStatus.APPROVED)
                .build();
    }

    private double counter(String type, String outcome) {
        var c = meterRegistry.find("spot.search.result")
                .tag("type", type)
                .tag("outcome", outcome)
                .counter();
        return c == null ? 0d : c.count();
    }

    private long timerCount(String type, String phase, String filtered) {
        var t = meterRegistry.find("spot.search.duration")
                .tag("type", type)
                .tag("phase", phase)
                .tag("filtered", filtered)
                .timer();
        return t == null ? 0L : t.count();
    }

    @Test
    @DisplayName("키워드 검색 결과가 0건이면 outcome=zero로 집계된다")
    void keywordSearch_noResult_countsZeroOutcome() {
        given(spotRepository.searchSpots(eq("없는키워드"), eq(SpotStatus.APPROVED), any()))
                .willReturn(new PageImpl<>(List.of()));

        spotService.searchSpots("없는키워드", null, 0, 20);

        assertThat(counter("keyword", "zero")).isEqualTo(1d);
        assertThat(counter("keyword", "hit")).isEqualTo(0d);
    }

    @Test
    @DisplayName("키워드 검색은 쿼리 구간과 매핑 구간을 각각 기록한다")
    void keywordSearch_recordsQueryAndMappingPhases() {
        given(spotRepository.searchSpots(eq("한라산"), eq(SpotStatus.APPROVED), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        spotService.searchSpots("한라산", null, 0, 20);

        assertThat(timerCount("keyword", "query", "false")).isEqualTo(1L);
        assertThat(timerCount("keyword", "mapping", "false")).isEqualTo(1L);
        assertThat(counter("keyword", "hit")).isEqualTo(1d);
    }

    @Test
    @DisplayName("카테고리 필터가 붙은 검색은 filtered=true로 분리 집계된다")
    void keywordSearch_withCategory_taggedAsFiltered() {
        given(spotRepository.searchSpotsByCategories(eq("한라산"), any(), eq(SpotStatus.APPROVED), any()))
                .willReturn(new PageImpl<>(List.of(spot())));

        spotService.searchSpots("한라산", List.of("MOUNTAIN"), 0, 20);

        assertThat(timerCount("keyword", "query", "true")).isEqualTo(1L);
        assertThat(timerCount("keyword", "query", "false")).isEqualTo(0L);
    }

    @Test
    @DisplayName("지도 조회도 type=map으로 동일하게 계측된다")
    void mapSearch_recordsMapTypeMetrics() {
        given(spotRepository.findSpotsInMapBounds(any(), any(), any(), any(), eq(SpotStatus.APPROVED), any()))
                .willReturn(List.of(spot()));

        spotService.getMapSpots(new MapBoundsRequest(37.4, 126.9, 37.6, 127.1, null, 100));

        assertThat(timerCount("map", "query", "false")).isEqualTo(1L);
        assertThat(timerCount("map", "mapping", "false")).isEqualTo(1L);
        assertThat(counter("map", "hit")).isEqualTo(1d);
    }
}
