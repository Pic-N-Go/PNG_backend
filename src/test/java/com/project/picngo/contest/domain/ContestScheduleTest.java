package com.project.picngo.contest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주기 규칙(출품 2주 → 투표 2주 → 투표 종료 익일 09:00 발표)이 깨지면 여기서 걸린다.
 * 손으로 INSERT하던 시절에는 이 규칙을 검증할 자리가 없었다.
 */
class ContestScheduleTest {

    private static Contest contest(LocalDateTime submitStartAt) {
        return Contest.create("골든아워", "설명", null, submitStartAt, 3, 3);
    }

    @Test
    @DisplayName("출품 2주 · 투표 2주 · 발표는 투표 종료 다음 날 오전 9시")
    void derivesPeriodsFromSubmitStart() {
        Contest contest = contest(LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(contest.getSubmitEndAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 0, 0));
        // 투표 시작은 출품 종료와 붙어 있어야 한다 — getPhase가 submitEndAt을 투표 시작으로 보기 때문
        assertThat(contest.getVoteStartAt()).isEqualTo(contest.getSubmitEndAt());
        assertThat(contest.getVoteEndAt()).isEqualTo(LocalDateTime.of(2026, 9, 29, 0, 0));
        assertThat(contest.getResultOpenAt()).isEqualTo(LocalDateTime.of(2026, 9, 30, 9, 0));
    }

    @Test
    @DisplayName("투표가 자정이 아닌 시각에 끝나도 발표는 그 다음 날 09:00이다")
    void announcesAtNineOnTheDayAfterVoteEnd() {
        Contest contest = contest(LocalDateTime.of(2026, 9, 1, 18, 30));

        assertThat(contest.getVoteEndAt()).isEqualTo(LocalDateTime.of(2026, 9, 29, 18, 30));
        assertThat(contest.getResultOpenAt()).isEqualTo(LocalDateTime.of(2026, 9, 30, 9, 0));
    }

    @Test
    @DisplayName("시작 전에는 UPCOMING이다 — 출품 API가 몇 주 일찍 열리지 않게 하는 유일한 방어")
    void beforeSubmitStartIsUpcoming() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        Contest contest = contest(start);

        assertThat(contest.getPhase(start.minusDays(40))).isEqualTo(ContestPhase.UPCOMING);
        assertThat(contest.getPhase(start.minusMinutes(1))).isEqualTo(ContestPhase.UPCOMING);
    }

    @Test
    @DisplayName("phase가 예정 → 출품 → 투표 → 집계중 → 종료 순으로 넘어간다")
    void movesThroughPhases() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        Contest contest = contest(start);

        assertThat(contest.getPhase(start.minusSeconds(1))).isEqualTo(ContestPhase.UPCOMING);
        assertThat(contest.getPhase(start)).isEqualTo(ContestPhase.SUBMITTING);
        assertThat(contest.getPhase(start.plusDays(13))).isEqualTo(ContestPhase.SUBMITTING);
        assertThat(contest.getPhase(contest.getSubmitEndAt())).isEqualTo(ContestPhase.VOTING);
        assertThat(contest.getPhase(start.plusDays(27))).isEqualTo(ContestPhase.VOTING);
        // 투표 종료 ~ 발표 사이는 집계 중이고, 이 구간에서는 결과가 공개되지 않는다
        assertThat(contest.getPhase(contest.getVoteEndAt())).isEqualTo(ContestPhase.RESULT);
        assertThat(contest.getPhase(contest.getResultOpenAt().minusMinutes(1))).isEqualTo(ContestPhase.RESULT);
        assertThat(contest.getPhase(contest.getResultOpenAt())).isEqualTo(ContestPhase.ENDED);
    }
}
