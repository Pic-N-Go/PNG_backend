package com.project.picngo.bookmark.repository;

import com.project.picngo.bookmark.domain.BookmarkCollection;
import com.project.picngo.bookmark.domain.BookmarkCollectionSpot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * findBookmarkedSpotIds는 목록 응답의 isBookmarked를 채우는 유일한 경로다.
 * collection을 거치는 암묵 조인·distinct·유저 격리가 실제 쿼리로 성립하는지 고정한다
 * (서비스 단위 테스트는 이 리포지토리를 목으로 막으므로 조인이 틀려도 통과한다).
 */
@DataJpaTest
@ActiveProfiles("test")
class BookmarkedSpotIdsQueryTest {

    private static final Long MY_ID = 1L;
    private static final Long OTHER_ID = 2L;

    @Autowired
    private BookmarkCollectionRepository collectionRepository;

    @Autowired
    private BookmarkCollectionSpotRepository membershipRepository;

    private BookmarkCollection collection(Long userId, String name) {
        return collectionRepository.save(BookmarkCollection.builder()
                .userId(userId)
                .name(name)
                .color("pink")
                .icon("star")
                .build());
    }

    private void addSpot(BookmarkCollection collection, long spotId) {
        membershipRepository.save(BookmarkCollectionSpot.builder()
                .collection(collection)
                .spotId(spotId)
                .build());
    }

    @Test
    @DisplayName("같은 스팟이 내 컬렉션 여러 개에 담겨 있어도 한 번만 반환된다 (distinct)")
    void sameSpotInMultipleCollections_returnedOnce() {
        addSpot(collection(MY_ID, "즐겨찾기"), 10L);
        addSpot(collection(MY_ID, "가을 출사"), 10L);

        List<Long> result = membershipRepository.findBookmarkedSpotIds(MY_ID, List.of(10L));

        assertThat(result).containsExactly(10L);
    }

    @Test
    @DisplayName("다른 유저가 담은 스팟은 내 결과에 섞이지 않는다")
    void otherUsersBookmarks_doNotLeak() {
        addSpot(collection(MY_ID, "즐겨찾기"), 10L);
        addSpot(collection(OTHER_ID, "즐겨찾기"), 20L);

        List<Long> result = membershipRepository.findBookmarkedSpotIds(MY_ID, List.of(10L, 20L));

        assertThat(result).containsExactly(10L);
    }

    @Test
    @DisplayName("어느 컬렉션에도 없는 스팟은 반환되지 않는다")
    void unbookmarkedSpot_absent() {
        addSpot(collection(MY_ID, "즐겨찾기"), 10L);

        List<Long> result = membershipRepository.findBookmarkedSpotIds(MY_ID, List.of(10L, 11L, 12L));

        assertThat(result).containsExactly(10L);
    }

    @Test
    @DisplayName("조회 범위(IN 절)에 없는 스팟은 북마크돼 있어도 반환되지 않는다")
    void spotOutsideRequestedIds_absent() {
        BookmarkCollection mine = collection(MY_ID, "즐겨찾기");
        addSpot(mine, 10L);
        addSpot(mine, 99L);

        List<Long> result = membershipRepository.findBookmarkedSpotIds(MY_ID, List.of(10L));

        assertThat(result).containsExactly(10L);
    }

    // findSpotIdsByUserId / findSpotIdsByCollectionId — MY 탭 "북마크한 스팟"의 유일한 조회 경로.
    // 서비스는 이 리포지토리를 목으로 막으므로 조인·정렬이 틀려도 단위 테스트는 통과한다.

    @Test
    @DisplayName("findSpotIdsByUserId는 다른 유저가 담은 스팟을 섞지 않는다")
    void spotIdsByUserId_isolatedPerUser() {
        addSpot(collection(MY_ID, "즐겨찾기"), 10L);
        addSpot(collection(OTHER_ID, "즐겨찾기"), 20L);

        assertThat(membershipRepository.findSpotIdsByUserId(MY_ID)).containsExactly(10L);
        assertThat(membershipRepository.findSpotIdsByUserId(OTHER_ID)).containsExactly(20L);
    }

    @Test
    @DisplayName("findSpotIdsByUserId는 최근 담은 순으로, 여러 컬렉션에 담긴 스팟은 한 번만 반환한다")
    void spotIdsByUserId_dedupedAndOrderedByRecency() {
        BookmarkCollection first = collection(MY_ID, "즐겨찾기");
        BookmarkCollection second = collection(MY_ID, "가을 출사");
        addSpot(first, 10L);
        addSpot(first, 20L);
        // 20번을 두 번째 컬렉션에도 담는다 → 중복 없이, 가장 최근 담은 시점으로 줄을 선다.
        addSpot(second, 30L);
        addSpot(second, 20L);

        assertThat(membershipRepository.findSpotIdsByUserId(MY_ID)).containsExactly(20L, 30L, 10L);
    }

    @Test
    @DisplayName("findSpotIdsByCollectionId는 해당 컬렉션 것만 최근 담은 순으로 반환한다")
    void spotIdsByCollectionId_scopedAndOrdered() {
        BookmarkCollection mine = collection(MY_ID, "즐겨찾기");
        BookmarkCollection other = collection(MY_ID, "가을 출사");
        addSpot(mine, 10L);
        addSpot(mine, 20L);
        addSpot(other, 30L);

        assertThat(membershipRepository.findSpotIdsByCollectionId(mine.getId())).containsExactly(20L, 10L);
        assertThat(membershipRepository.findSpotIdsByCollectionId(other.getId())).containsExactly(30L);
    }
}
