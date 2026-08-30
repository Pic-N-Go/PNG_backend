package com.project.picngo.contest.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ContestErrorCode;
import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestEntry;
import com.project.picngo.contest.repository.ContestEntryRepository;
import com.project.picngo.contest.repository.ContestRepository;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 내 출품작에는 투표할 수 없다.
 *
 * 1인 3표라 전원이 자기 작품에 한 표씩만 줘도 순위가 그만큼 왜곡된다.
 * 화면 세 곳(진행중 그리드·전체 목록·상세)이 모두 버튼을 가리지만, 막는 책임은 서버에 있다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContestSelfVoteTest {

    @Mock private ContestRepository contestRepository;
    @Mock private ContestEntryRepository contestEntryRepository;
    @Mock private ContestVoteRepository contestVoteRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.project.picngo.contest.repository.ContestSubscriptionRepository subscriptionRepository;
    @Mock private com.project.picngo.contest.repository.ContestRankingSnapshotRepository rankingSnapshotRepository;
    @Mock private com.project.picngo.contest.repository.ContestReportRepository contestReportRepository;
    @Mock private com.project.picngo.spot.repository.SpotRepository spotRepository;
    @Mock private com.project.picngo.common.image.service.ImageStorageService imageStorageService;
    @Mock private com.project.picngo.common.image.service.ExifExtractor exifExtractor;

    @InjectMocks private ContestService contestService;

    private static final Long VOTER = 7L;

    /** 투표 기간에 놓인 회차 — 출품 2주가 끝나고 투표가 진행 중이다 */
    private Contest votingContest() {
        return Contest.create("골든아워", null, null, LocalDateTime.now(Contest.ZONE).minusWeeks(3), 3, 3);
    }

    private void givenEntryOwnedBy(Long ownerId) {
        User voter = mock(User.class);
        given(voter.getId()).willReturn(VOTER);
        given(userRepository.findById(VOTER)).willReturn(Optional.of(voter));

        User owner = mock(User.class);
        given(owner.getId()).willReturn(ownerId);
        ContestEntry entry = mock(ContestEntry.class);
        given(entry.getUser()).willReturn(owner);
        given(entry.getId()).willReturn(100L);

        given(contestRepository.findById(anyLong())).willReturn(Optional.of(votingContest()));
        given(contestEntryRepository.findByIdAndContest(anyLong(), any())).willReturn(Optional.of(entry));
        given(contestVoteRepository.existsByEntryAndUser(any(), any())).willReturn(false);
        given(contestVoteRepository.countByContestAndUser(any(), any())).willReturn(0L);
    }

    @Test
    @DisplayName("내 출품작에 투표하면 409로 거절한다")
    void rejectsVotingOwnEntry() {
        givenEntryOwnedBy(VOTER);

        assertThatThrownBy(() -> contestService.voteEntry(1L, 100L, VOTER))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CANNOT_VOTE_OWN_ENTRY);
    }

    @Test
    @DisplayName("남의 출품작은 그대로 투표된다 — 차단이 과하게 걸리지 않는다")
    void allowsVotingOthersEntry() {
        givenEntryOwnedBy(999L);

        assertThatCode(() -> contestService.voteEntry(1L, 100L, VOTER)).doesNotThrowAnyException();
    }
}
