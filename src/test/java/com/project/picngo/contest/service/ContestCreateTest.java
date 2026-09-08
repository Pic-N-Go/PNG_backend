package com.project.picngo.contest.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ContestErrorCode;
import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestPhase;
import com.project.picngo.contest.dto.AdminContestDetailResponse;
import com.project.picngo.contest.dto.ContestCreateRequest;
import com.project.picngo.contest.dto.ContestResponse;
import com.project.picngo.contest.dto.ContestUpdateRequest;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private com.project.picngo.notification.service.NotificationService notificationService;

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
    @DisplayName("지난 시각으로 시작일을 지정하면 거절한다 — 개설하자마자 끝나 있는 회차가 된다")
    void rejectsPastSubmitStartAt() {
        givenUser();
        givenLastContest(null);

        assertThatThrownBy(() -> contestService.createContest(
                USER_ID, request(LocalDateTime.now(Contest.ZONE).minusDays(1))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_START_IN_PAST);
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

    @Test
    @DisplayName("콘테스트 강제 마감 및 결과 발표 시 상태가 ENDED로 변하고 참가자/투표자/구독자에게 알림이 발송된다")
    void forcePublishResultClosesContestAndNotifiesUsers() {
        LocalDateTime now = LocalDateTime.now(Contest.ZONE);
        Contest contest = Contest.create("가을 단풍전", "설명", "url", now.minusWeeks(1), 3, 3);
        org.springframework.test.util.ReflectionTestUtils.setField(contest, "id", 1L);

        given(contestRepository.findById(1L)).willReturn(Optional.of(contest));
        given(contestRepository.save(any(Contest.class))).willAnswer(call -> call.getArgument(0));

        given(subscriptionRepository.findDistinctUserIdsByContest(contest)).willReturn(List.of(10L, 20L));
        given(contestEntryRepository.findDistinctUserIdsByContest(contest)).willReturn(List.of(20L, 30L));
        given(contestVoteRepository.findDistinctUserIdsByContest(contest)).willReturn(List.of(30L, 40L));

        AdminContestDetailResponse response = contestService.forcePublishResult(1L);

        assertThat(response.phase()).isEqualTo(ContestPhase.ENDED);
        assertThat(contest.isResultNotificationSent()).isTrue();

        // 10L, 20L, 30L, 40L 총 4명(중복 제거된 합집합)에게 각각 1회씩 발송
        org.mockito.Mockito.verify(notificationService, org.mockito.Mockito.times(4))
                .sendPushNotification(any(), org.mockito.ArgumentMatchers.eq("COMMUNITY_CONTEST_RESULT"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("출품 전(UPCOMING) 상태인 콘테스트의 시작 날짜를 변경하면 전체 일정이 자동 재계산된다")
    void updateContestScheduleInUpcomingPhase() {
        LocalDateTime now = LocalDateTime.now(Contest.ZONE);
        LocalDateTime originalStart = now.plusWeeks(1);
        LocalDateTime newStart = now.plusWeeks(2);

        Contest contest = Contest.create("가을 단풍전", "설명", "url", originalStart, 3, 3);
        org.springframework.test.util.ReflectionTestUtils.setField(contest, "id", 1L);

        given(contestRepository.findById(1L)).willReturn(Optional.of(contest));
        given(contestRepository.save(any(Contest.class))).willAnswer(call -> call.getArgument(0));
        given(contestRepository.existsOverlappingContest(eq(1L), eq(newStart), any())).willReturn(false);

        ContestUpdateRequest request = new ContestUpdateRequest(
                "수정된 단풍전", "새 설명", "new-url.jpg", newStart
        );

        AdminContestDetailResponse response = contestService.updateContest(1L, request);

        assertThat(response.title()).isEqualTo("수정된 단풍전");
        assertThat(response.submitStartAt()).isEqualTo(newStart);
        assertThat(response.submitEndAt()).isEqualTo(newStart.plusWeeks(2));
        assertThat(response.voteEndAt()).isEqualTo(newStart.plusWeeks(4));
    }

    @Test
    @DisplayName("출품이 이미 시작된(SUBMITTING) 콘테스트의 시작 날짜를 변경하려 하면 예외가 발생한다")
    void updateContestFailsWhenAlreadyStarted() {
        LocalDateTime now = LocalDateTime.now(Contest.ZONE);
        LocalDateTime originalStart = now.minusDays(3); // 이미 시작됨 (SUBMITTING)

        Contest contest = Contest.create("가을 단풍전", "설명", "url", originalStart, 3, 3);
        org.springframework.test.util.ReflectionTestUtils.setField(contest, "id", 1L);

        given(contestRepository.findById(1L)).willReturn(Optional.of(contest));

        ContestUpdateRequest request = new ContestUpdateRequest(
                null, null, null, now.plusWeeks(1)
        );

        assertThatThrownBy(() -> contestService.updateContest(1L, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CANNOT_MODIFY_STARTED_CONTEST);
    }

    @Test
    @DisplayName("새로운 시작일을 과거로 변경하려 하면 예외가 발생한다")
    void updateContestFailsWhenNewStartInPast() {
        LocalDateTime now = LocalDateTime.now(Contest.ZONE);
        LocalDateTime originalStart = now.plusWeeks(1);

        Contest contest = Contest.create("가을 단풍전", "설명", "url", originalStart, 3, 3);
        org.springframework.test.util.ReflectionTestUtils.setField(contest, "id", 1L);

        given(contestRepository.findById(1L)).willReturn(Optional.of(contest));

        ContestUpdateRequest request = new ContestUpdateRequest(
                null, null, null, now.minusDays(1)
        );

        assertThatThrownBy(() -> contestService.updateContest(1L, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_START_IN_PAST);
    }
}
