package com.project.picngo.contest.service;

import com.project.picngo.contest.domain.ContestPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 득표순 정렬은 숫자를 가려도 순서가 곧 순위다.
 *
 * 투표 기간에는 그게 의도된 노출이지만(목업이 득표순 칩을 둔다), 투표가 끝난 뒤의 순서는
 * 최종 결과라 발표 전까지 열어두면 /result를 막아둔 의미가 없다.
 */
class ContestEntrySortTest {

    private static boolean sortsByVotes(Sort sort) {
        return sort.getOrderFor("voteCount") != null;
    }

    @Test
    @DisplayName("투표 기간과 발표 후에는 득표순이 그대로 먹는다")
    void votesSortAllowedWhenOrderIsPublic() {
        assertThat(sortsByVotes(ContestService.resolveEntrySort("votes", ContestPhase.VOTING))).isTrue();
        assertThat(sortsByVotes(ContestService.resolveEntrySort("votes", ContestPhase.ENDED))).isTrue();
    }

    @Test
    @DisplayName("집계 중에는 득표순을 최신순으로 내린다 — 순서가 곧 발표 전 결과다")
    void votesSortDowngradedWhileCounting() {
        Sort sort = ContestService.resolveEntrySort("votes", ContestPhase.RESULT);

        assertThat(sortsByVotes(sort)).isFalse();
        assertThat(sort.getOrderFor("createdAt")).isNotNull();
    }

    @Test
    @DisplayName("출품 기간에도 내리지만 이건 안전 때문이 아니라 표가 아직 0이라 의미가 없어서다")
    void votesSortDowngradedWhileSubmitting() {
        assertThat(sortsByVotes(ContestService.resolveEntrySort("votes", ContestPhase.SUBMITTING))).isFalse();
    }

    @Test
    @DisplayName("기본값과 모르는 값은 최신순")
    void defaultsToLatest() {
        assertThat(sortsByVotes(ContestService.resolveEntrySort("latest", ContestPhase.VOTING))).isFalse();
        assertThat(sortsByVotes(ContestService.resolveEntrySort("무엇", ContestPhase.VOTING))).isFalse();
    }
}
