package com.project.picngo.contest.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ContestErrorCode;
import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.dto.ContestCreateRequest;
import com.project.picngo.contest.dto.ContestResponse;
import com.project.picngo.contest.repository.ContestEntryRepository;
import com.project.picngo.contest.repository.ContestRepository;
import com.project.picngo.contest.repository.ContestSubscriptionRepository;
import com.project.picngo.contest.repository.ContestVoteRepository;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 회차 개설의 시작일 결정 규칙.
 *
 * 직전 회차의 발표 시각에 이어 붙이지 않으면, 집계 중 구간에서 getCurrentContest가 새 회차를
 * 골라 발표를 기다리는 직전 회차가 current에도 past에도 안 잡히는 사각지대가 생긴다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContestCreateTest {

    @Mock private ContestRepository contestRepository;
    @Mock private ContestEntryRepository contestEntryRepository;
    @Mock private ContestVoteRepository contestVoteRepository;
    @Mock private ContestSubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    // toContestResponse가 테마 이미지에도 presign을 태운다 — 여기선 값이 null이라 호출만 통과하면 된다
    @Mock private com.project.picngo.common.image.service.ImageStorageService imageStorageService;
    @Mock private com.project.picngo.common.image.service.ExifExtractor exifExtractor;
    @Mock private com.project.picngo.spot.repository.SpotRepository spotRepository;
    @Mock private com.project.picngo.contest.repository.ContestRankingSnapshotRepository rankingSnapshotRepository;
    @Mock private com.project.picngo.contest.repository.ContestReportRepository contestReportRepository;

    @InjectMocks private ContestService contestService;

    private static final Long USER_ID = 1L;

    private void givenUser() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(mock(User.class)));
    }

    /** 저장된 Contest를 그대로 돌려준다 — 응답의 날짜가 곧 계산 결과다 */
    private void givenSaveEchoes() {
        given(contestRepository.save(any(Contest.class))).willAnswer(call -> call.getArgument(0));
    }

    private void givenLastContest(Contest last) {
        given(contestRepository.findFirstByOrderByResultOpenAtDesc()).willReturn(Optional.ofNullable(last));
    }

    private static ContestCreateRequest request(LocalDateTime submitStartAt) {
        return new ContestCreateRequest("골든아워", "설명", null, submitStartAt, null, null);
    }

    @Test
    @DisplayName("회차가 하나도 없으면 지금부터 시작한다")
    void firstContestStartsNow() {
        givenUser();
        givenSaveEchoes();
        givenLastContest(null);

        ContestResponse response = contestService.createContest(USER_ID, request(null));

        assertThat(response.submitStartAt()).isNotNull();
        // 규칙대로 파생됐는지 — 출품 2주 뒤가 마감
        assertThat(response.submitEndAt()).isEqualTo(response.submitStartAt().plusWeeks(2));
        assertThat(response.voteEndAt()).isEqualTo(response.submitStartAt().plusWeeks(4));
    }

    @Test
    @DisplayName("직전 회차가 아직 발표 전이면 그 발표 시각에 이어 붙는다")
    void chainsOntoPendingContest() {
        LocalDateTime lastResultOpenAt = LocalDateTime.now(Contest.ZONE).plusDays(3).withNano(0);
        givenUser();
        givenSaveEchoes();
        Contest last = Contest.create("직전", null, null, lastResultOpenAt.minusWeeks(4), 3, 3);
        givenLastContest(last);

        ContestResponse response = contestService.createContest(USER_ID, request(null));

        assertThat(response.submitStartAt()).isEqualTo(last.getResultOpenAt());
    }

    @Test
    @DisplayName("직전 회차가 이미 끝났으면 지금부터 — 과거 시각으로 시작하지 않는다")
    void doesNotStartInThePast() {
        LocalDateTime before = LocalDateTime.now(Contest.ZONE);
        givenUser();
        givenSaveEchoes();
        // 발표가 이미 지난 회차
        givenLastContest(Contest.create("옛날", null, null, before.minusDays(120), 3, 3));

        ContestResponse response = contestService.createContest(USER_ID, request(null));

        assertThat(response.submitStartAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("직전 회차 발표 전으로 당겨 열면 거절한다")
    void rejectsOverlap() {
        LocalDateTime lastResultOpenAt = LocalDateTime.now(Contest.ZONE).plusDays(10);
        givenUser();
        givenLastContest(Contest.create("직전", null, null, lastResultOpenAt.minusWeeks(4), 3, 3));

        assertThatThrownBy(() -> contestService.createContest(USER_ID, request(LocalDateTime.now(Contest.ZONE))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_PERIOD_OVERLAP);
    }

    @Test
    @DisplayName("직전 회차 발표 시각 정각은 허용된다 — 기본 체이닝이 만드는 값이라 막으면 안 된다")
    void allowsExactlyAtPreviousResultOpen() {
        givenUser();
        givenSaveEchoes();
        Contest last = Contest.create("직전", null, null, LocalDateTime.now(Contest.ZONE).minusWeeks(2), 3, 3);
        givenLastContest(last);

        assertThatCode(() -> contestService.createContest(USER_ID, request(last.getResultOpenAt())))
                .doesNotThrowAnyException();
    }
}
