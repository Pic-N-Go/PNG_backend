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

import com.project.picngo.contest.domain.ContestEntry;
import com.project.picngo.contest.dto.ContestEntryDetailResponse;
import com.project.picngo.contest.dto.ContestEntryPageResponse;
import com.project.picngo.contest.dto.ContestEntryResponse;
import com.project.picngo.common.image.service.ImageStorageService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Comparator;
import java.util.List;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContestService {

    private final ContestRepository contestRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final ContestVoteRepository contestVoteRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;

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

    // 출품작 목록 조회
    public ContestEntryPageResponse getContestEntries(
            Long contestId,
            Long userId,
            String sort,
            int page,
            int size
    ){
        User user = getUser(userId);
        Contest contest = getContestById(contestId);
        ContestPhase phase = contest.getPhase(LocalDateTime.now());

        Sort entrySort = resolveEntrySort(sort);
        Page<ContestEntry> entries = contestEntryRepository.findAllByContest(
                contest,
                PageRequest.of(page, size, entrySort)
        );

        boolean showRanking = canShowRanking(phase);

        List<ContestEntryResponse> responses = entries.getContent().stream()
                .map(entry -> ContestEntryResponse.from(
                        entry,
                        imageStorageService.getPresignedUrl(entry.getPhotoUrl()),
                        showRanking,
                        showRanking ? calculateRank(contest, entry) : null,
                        contestVoteRepository.existsByEntryAndUser(entry, user),
                        entry.getUser().getId().equals(user.getId())
                ))
                .toList();

        return new ContestEntryPageResponse(
                responses,
                entries.getNumber(),
                entries.getSize(),
                entries.getTotalElements(),
                entries.getTotalPages(),
                entries.isLast()
        );
    }

    // 출품작 상세 조회
    public ContestEntryDetailResponse getContestEntry(
            Long contestId,
            Long entryId,
            Long userId
    ){
        User user = getUser(userId);
        Contest contest = getContestById(contestId);
        ContestEntry entry = getEntryById(entryId);

        validateEntryContest(entry, contest);

        ContestPhase phase = contest.getPhase(LocalDateTime.now());
        boolean showRanking = canShowRanking(phase);
        boolean voted = contestVoteRepository.existsByEntryAndUser(entry, user);
        boolean mine = entry.getUser().getId().equals(user.getId());
        long usedVoteCount = contestVoteRepository.countByContestAndUser(contest, user);
        long remainingVoteCount = Math.max(0, contest.getVoteLimit() - usedVoteCount);

        return ContestEntryDetailResponse.from(
                entry,
                imageStorageService.getPresignedUrl(entry.getPhotoUrl()),
                phase,
                showRanking,
                showRanking ? calculateRank(contest, entry) : null,
                voted,
                mine,
                phase == ContestPhase.VOTING && !mine,
                mine && (phase == ContestPhase.SUBMITTING || phase == ContestPhase.VOTING),
                contest.getVoteLimit(),
                remainingVoteCount
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

    // 출품작 정렬 기준
    private Sort resolveEntrySort(String sort) {
        if("votes".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "voteCount")
                    .and(Sort.by(Sort.Direction.ASC,"createdAt"));
        }

        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    // 순위/득표 수 공개 여부
    private boolean canShowRanking(ContestPhase phase) {
        return phase == ContestPhase.RESULT || phase == ContestPhase.ENDED;
    }

    // 출품작 순위 계산
    private Integer calculateRank(Contest contest, ContestEntry targetEntry){
        List<ContestEntry> entries = contestEntryRepository.findAllByContest(contest, Pageable.unpaged())
                .getContent()
                .stream()
                .sorted(
                        Comparator.comparingInt(ContestEntry::getVoteCount).reversed()
                                .thenComparing(ContestEntry::getCreatedAt)
                )
                .toList();

        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getId().equals(targetEntry.getId())) {
                return i + 1;
            }
        }

        return null;
    }


    private Contest getContestById(Long contestId) {
        return contestRepository.findById(contestId)
                .orElseThrow(() -> new CustomException(ContestErrorCode.CONTEST_NOT_FOUND));
    }

    // 출품작 조회
    private ContestEntry getEntryById(Long entryId) {
        return contestEntryRepository.findById(entryId)
                .orElseThrow(() -> new CustomException(ContestErrorCode.ENTRY_NOT_FOUND));
    }

    // 출품작이 해당 콘테스트 소속인지 확인
    private void validateEntryContest(ContestEntry entry, Contest contest) {
        if (!entry.getContest().getId().equals(contest.getId())) {
            throw new CustomException(ContestErrorCode.ENTRY_NOT_FOUND);
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }

}
