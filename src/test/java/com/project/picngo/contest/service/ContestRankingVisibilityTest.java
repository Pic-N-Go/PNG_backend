package com.project.picngo.contest.service;

import com.project.picngo.contest.domain.ContestPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 투표 기간에 서열이 새지 않는지 지키는 테스트.
 *
 * 프론트는 투표 기간에 개별 작품의 순위·득표수를 어디에도 그리지 않고
 * (전체 출품작 그리드는 사진만, 내 출품 탭은 "집계 중"),
 * 서열은 순위 변동 패널의 상위 3개로만 드러난다.
 */
class ContestRankingVisibilityTest {

    @Test
    @DisplayName("개별 작품의 순위·득표수는 결과 발표 후에만 공개된다")
    void entryRankingOnlyAfterAnnouncement() {
        assertThat(ContestService.canShowRanking(ContestPhase.SUBMITTING)).isFalse();
        assertThat(ContestService.canShowRanking(ContestPhase.VOTING)).isFalse();
        // 투표는 끝났지만 아직 발표 전(집계 중) — 여기서 열리면 발표 시각이 무의미해진다
        assertThat(ContestService.canShowRanking(ContestPhase.RESULT)).isFalse();
        assertThat(ContestService.canShowRanking(ContestPhase.ENDED)).isTrue();
    }

    @Test
    @DisplayName("순위 변동 스냅샷(상위 3개)은 투표 기간부터 공개된다")
    void rankingHistoryFromVoting() {
        assertThat(ContestService.canShowRankingHistory(ContestPhase.SUBMITTING)).isFalse();
        assertThat(ContestService.canShowRankingHistory(ContestPhase.VOTING)).isTrue();
        assertThat(ContestService.canShowRankingHistory(ContestPhase.RESULT)).isTrue();
        assertThat(ContestService.canShowRankingHistory(ContestPhase.ENDED)).isTrue();
    }
}
