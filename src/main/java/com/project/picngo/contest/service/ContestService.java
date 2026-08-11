package com.project.picngo.contest.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ContestErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestPhase;
import com.project.picngo.contest.dto.ContestPastPageResponse;
import com.project.picngo.contest.dto.ContestPastResponse;
import com.project.picngo.contest.dto.ContestResponse;
import com.project.picngo.contest.repository.ContestEntryRepository;
import com.project.picngo.contest.repository.ContestRepository;
import com.project.picngo.contest.repository.ContestVoteRepository;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContestService {

    private final ContestRepository contestRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final ContestVoteRepository contestVoteRepository;
    private final UserRepository userRepository;

    // 현재 진행 중인 콘테스트 조회
    public ContestResponse getCurrentContest(Long userId){
        User user = getUser(userId);
        LocalDateTime now = LocalDateTime.now();

        Contest contest = contestRepository
                .findFirstBySubmitStartAtLessThanEqualAndResultOpenAtGreaterThanOrderBySubmitStartAtDesc(now, now)
                .orElseThrow(()-> new CustomException(ContestErrorCode.CURRENT_CONTEST_NOT_FOUND));

        return toContestResponse(contest, user, now);
    }

    // 콘테스트 상세 조회
    public ContestResponse getContest(Long contestId, Long userId){
        User user = getUser(userId);
        Contest contest = getContestById(contestId);

        return toContestResponse(contest, user, LocalDateTime.now());
    }

    // 지난 콘테스트 목록 조회
    public ContestPastPageResponse getPastContests(Long userId, Pageable pageable){
        User user = getUser(userId);
        LocalDateTime now = LocalDateTime.now();

        Page<Contest> contests = contestRepository.findAll(pageable);

        Page<ContestPastResponse> responses = contests
                .map(contest -> {
                    int entryCount = (int) contestEntryRepository.countByContest(contest);
                    long participantCount = contestEntryRepository.countDistinctUserByContest(contest);

                    return new ContestPastResponse(
                            contest.getId(),
                            contest.getTitle(),
                            contest.getThemeImageUrl(),
                            entryCount,
                            participantCount,
                            0,      // 전체 득표 수는 결과 API에서 더 정확히 계산 예정
                            null,   // 내 순위는 이후 my-history/result에서 계산 예정
                            null    // 우승자 닉네임은 결과 조회 구현 후 연결
                    );
                });

        return new ContestPastPageResponse(
                responses.getContent(),
                responses.getNumber(),
                responses.getSize(),
                responses.getTotalElements(),
                responses.getTotalPages(),
                responses.isLast()
        );
    }

    private ContestResponse toContestResponse(Contest contest, User user, LocalDateTime now) {
        ContestPhase phase = contest.getPhase(now);
        int entryCount = (int) contestEntryRepository.countByContest(contest);
        long participantCount = contestEntryRepository.countDistinctUserByContest(contest);
        int myEntryCount = (int) contestEntryRepository.countByContestAndUser(contest, user);
        long usedVoteCount = contestVoteRepository.countByContestAndUser(contest, user);

        return ContestResponse.of(
                contest,
                phase,
                entryCount,
                participantCount,
                myEntryCount,
                usedVoteCount
        );
    }

    private Contest getContestById(Long contestId) {
        return contestRepository.findById(contestId)
                .orElseThrow(() -> new CustomException(ContestErrorCode.CONTEST_NOT_FOUND));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }

}
