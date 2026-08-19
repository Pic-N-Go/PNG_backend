package com.project.picngo.spot.service;

import com.project.picngo.bookmark.repository.BookmarkCollectionSpotRepository;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.config.SearchEngine;
import com.project.picngo.spot.config.SearchProperties;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.dto.SpotResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 목록 응답의 isBookmarked 채우기 검증.
 * 핵심은 두 가지 — 스팟마다 exists를 도는 N+1이 아니라 한 번에 조회하는 것,
 * 그리고 비로그인 요청에서는 쿼리를 아예 날리지 않는 것.
 */
@ExtendWith(MockitoExtension.class)
class SpotServiceBookmarkFlagTest {

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private BookmarkCollectionSpotRepository bookmarkCollectionSpotRepository;

    // searchSpots가 계측(SqlCounting·Timer)과 검색 단계 설정을 타므로 이 둘이 없으면 NPE가 난다.
    // 폴백은 전부 끈다 — 여기서 고정하는 건 북마크 플래그지 검색 단계가 아니다.
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Spy
    private SearchProperties searchProperties = new SearchProperties(SearchEngine.LIKE, false, false, false, false);

    @InjectMocks
    private SpotService spotService;

    private Spot spot(long id, String name) {
        Spot spot = Spot.builder()
                .name(name)
                .address("서울특별시 종로구")
                .latitude(37.5)
                .longitude(127.0)
                .categories(Set.of(SpotCategory.PARK))
                .status(SpotStatus.APPROVED)
                .build();
        // 빌더가 id를 받지 않는다(@GeneratedValue). 조회 결과를 흉내 내려면 직접 넣어야 한다.
        ReflectionTestUtils.setField(spot, "id", id);
        return spot;
    }

    @Test
    @DisplayName("로그인 사용자: 북마크한 스팟만 isBookmarked=true로 표시된다")
    void loggedIn_marksOnlyBookmarkedSpots() {
        given(spotRepository.findListByStatusAndIsActiveTrue(eq(SpotStatus.APPROVED), any()))
                .willReturn(List.of(spot(1L, "갈산공원"), spot(2L, "가회동성당"), spot(3L, "건청궁")));
        given(bookmarkCollectionSpotRepository.findBookmarkedSpotIds(eq(7L), anyCollection()))
                .willReturn(List.of(2L));

        List<SpotResponse> result = spotService.getPopularSpots(null, 10, 7L);

        assertThat(result).extracting(SpotResponse::id, SpotResponse::isBookmarked)
                .containsExactly(tuple(1L, false), tuple(2L, true), tuple(3L, false));
    }

    /**
     * getPopularSpots만 List.stream().map()이고, getSpots·searchSpots는 Page.getContent() + Page.map()이라
     * 플래그를 붙이는 코드 경로가 구조적으로 다르다. 클라이언트가 실제로 페이징하는 쪽을 따로 고정한다.
     */
    @Test
    @DisplayName("getSpots(Page 경로): 북마크한 스팟만 isBookmarked=true로 표시된다")
    void getSpots_marksOnlyBookmarkedSpots() {
        given(spotRepository.findAllByStatusAndIsActiveTrue(eq(SpotStatus.APPROVED), any()))
                .willReturn(new PageImpl<>(List.of(spot(1L, "갈산공원"), spot(2L, "가회동성당"), spot(3L, "건청궁"))));
        given(bookmarkCollectionSpotRepository.findBookmarkedSpotIds(eq(7L), anyCollection()))
                .willReturn(List.of(3L));

        Page<SpotResponse> result = spotService.getSpots(null, "popular", 0, 10, 7L);

        assertThat(result.getContent()).extracting(SpotResponse::id, SpotResponse::isBookmarked)
                .containsExactly(tuple(1L, false), tuple(2L, false), tuple(3L, true));
    }

    @Test
    @DisplayName("searchSpots(Page 경로): 북마크한 스팟만 isBookmarked=true로 표시된다")
    void searchSpots_marksOnlyBookmarkedSpots() {
        given(spotRepository.searchSpots(eq("공원"), eq(SpotStatus.APPROVED), any()))
                .willReturn(new PageImpl<>(List.of(spot(1L, "갈산공원"), spot(2L, "거리공원"))));
        given(bookmarkCollectionSpotRepository.findBookmarkedSpotIds(eq(7L), anyCollection()))
                .willReturn(List.of(1L));

        Page<SpotResponse> result = spotService.searchSpots("공원", null, 0, 10, 7L);

        assertThat(result.getContent()).extracting(SpotResponse::id, SpotResponse::isBookmarked)
                .containsExactly(tuple(1L, true), tuple(2L, false));
    }

    @Test
    @DisplayName("로그인 사용자: 스팟이 몇 개든 북마크 조회는 한 번만 나간다 (N+1 방지)")
    void loggedIn_queriesBookmarksOnce() {
        given(spotRepository.findListByStatusAndIsActiveTrue(eq(SpotStatus.APPROVED), any()))
                .willReturn(List.of(spot(1L, "A"), spot(2L, "B"), spot(3L, "C")));
        given(bookmarkCollectionSpotRepository.findBookmarkedSpotIds(eq(7L), anyCollection()))
                .willReturn(List.of());

        spotService.getPopularSpots(null, 10, 7L);

        verify(bookmarkCollectionSpotRepository).findBookmarkedSpotIds(eq(7L), anyCollection());
        verify(bookmarkCollectionSpotRepository, never()).existsByCollection_UserIdAndSpotId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("비로그인: 북마크 조회 없이 전부 false로 내려간다")
    void anonymous_skipsBookmarkQuery() {
        given(spotRepository.findListByStatusAndIsActiveTrue(eq(SpotStatus.APPROVED), any()))
                .willReturn(List.of(spot(1L, "갈산공원"), spot(2L, "가회동성당")));

        List<SpotResponse> result = spotService.getPopularSpots(null, 10, null);

        assertThat(result).extracting(SpotResponse::isBookmarked).containsOnly(false);
        verify(bookmarkCollectionSpotRepository, never()).findBookmarkedSpotIds(any(), anyCollection());
    }

    @Test
    @DisplayName("결과가 비면 로그인 상태여도 빈 IN 절 쿼리를 날리지 않는다")
    void emptyResult_skipsBookmarkQuery() {
        given(spotRepository.findListByStatusAndIsActiveTrue(eq(SpotStatus.APPROVED), any()))
                .willReturn(List.of());

        assertThat(spotService.getPopularSpots(null, 10, 7L)).isEmpty();
        verify(bookmarkCollectionSpotRepository, never()).findBookmarkedSpotIds(any(), anyCollection());
    }
}
