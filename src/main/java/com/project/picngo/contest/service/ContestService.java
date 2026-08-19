package com.project.picngo.contest.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ContestErrorCode;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.common.image.dto.ImageUploadResult;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestEntry;
import com.project.picngo.contest.domain.ContestPhase;
import com.project.picngo.contest.domain.ContestRankingSnapshot;
import com.project.picngo.contest.domain.ContestReport;
import com.project.picngo.contest.domain.ContestSubscription;
import com.project.picngo.contest.domain.ContestVote;
import com.project.picngo.contest.dto.ContestCreateEntryRequest;
import com.project.picngo.contest.dto.ContestEntryRankSummary;
import com.project.picngo.contest.dto.ContestEntryDetailResponse;
import com.project.picngo.contest.dto.ContestEntryPageResponse;
import com.project.picngo.contest.dto.ContestEntryResponse;
import com.project.picngo.contest.dto.ContestMyEntryResponse;
import com.project.picngo.contest.dto.ContestMyHistoryResponse;
import com.project.picngo.contest.dto.ContestMyRankSummary;
import com.project.picngo.contest.dto.ContestMyVoteResponse;
import com.project.picngo.contest.dto.ContestPastPageResponse;
import com.project.picngo.contest.dto.ContestPastResponse;
import com.project.picngo.contest.dto.ContestPastSummary;
import com.project.picngo.contest.dto.ContestRankingHistoryResponse;
import com.project.picngo.contest.dto.ContestReportRequest;
import com.project.picngo.contest.dto.ContestResponse;
import com.project.picngo.contest.dto.ContestResultResponse;
import com.project.picngo.contest.dto.ContestSubscriptionResponse;
import com.project.picngo.contest.dto.ContestVoteResponse;
import com.project.picngo.contest.repository.ContestEntryRepository;
import com.project.picngo.contest.repository.ContestRankingSnapshotRepository;
import com.project.picngo.contest.repository.ContestReportRepository;
import com.project.picngo.contest.repository.ContestRepository;
import com.project.picngo.contest.repository.ContestSubscriptionRepository;
import com.project.picngo.contest.repository.ContestVoteRepository;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContestService {

    private final ContestRepository contestRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final ContestVoteRepository contestVoteRepository;
    private final ContestRankingSnapshotRepository rankingSnapshotRepository;
    private final ContestSubscriptionRepository subscriptionRepository;
    private final ContestReportRepository contestReportRepository;
    private final UserRepository userRepository;
    private final SpotRepository spotRepository;
    private final ImageStorageService imageStorageService;

    // 현재 진행 중인 콘테스트 조회
    public ContestResponse getCurrentContest(Long userId) {
        User user = getUser(userId);
        LocalDateTime now = LocalDateTime.now();

        Contest contest = contestRepository
                .findFirstBySubmitStartAtLessThanEqualAndResultOpenAtGreaterThanOrderBySubmitStartAtDesc(now, now)
                .orElseThrow(() -> new CustomException(ContestErrorCode.CURRENT_CONTEST_NOT_FOUND));

        return toContestResponse(contest, user, now);
    }

    // 콘테스트 상세 조회
    public ContestResponse getContest(Long contestId, Long userId) {
        User user = getUser(userId);
        Contest contest = getContestById(contestId);

        return toContestResponse(contest, user, LocalDateTime.now());
    }

    // 지난 콘테스트 목록 조회
    public ContestPastPageResponse getPastContests(Long userId, Pageable pageable) {
        User user = getUser(userId);
        Page<Contest> contests = contestRepository.findAllByResultOpenAtBeforeOrderByResultOpenAtDesc(
                LocalDateTime.now(),
                pageable
        );

        List<Long> contestIds = contests.getContent().stream()
                .map(Contest::getId)
                .toList();

        if (contestIds.isEmpty()) {
            return new ContestPastPageResponse(
                    List.of(),
                    contests.getNumber(),
                    contests.getSize(),
                    contests.getTotalElements(),
                    contests.getTotalPages(),
                    contests.isLast()
            );
        }

        Map<Long, ContestPastSummary> summaryMap = contestEntryRepository
                .findPastSummariesByContestIds(contestIds)
                .stream()
                .collect(Collectors.toMap(ContestPastSummary::contestId, Function.identity()));

        Map<Long, ContestEntry> winnerMap = contestEntryRepository
                .findWinnersByContestIds(contestIds)
                .stream()
                .collect(Collectors.toMap(entry -> entry.getContest().getId(), Function.identity(), (first, second) -> first));

        Map<Long, Integer> myRankMap = contestEntryRepository
                .findMyRanksByContestIds(contestIds, user)
                .stream()
                .collect(Collectors.toMap(
                        ContestMyRankSummary::contestId,
                        rankSummary -> (int) rankSummary.rank(),
                        Math::min
                ));

        Page<ContestPastResponse> responses = contests.map(contest -> {
            ContestPastSummary summary = summaryMap.get(contest.getId());
            ContestEntry winner = winnerMap.get(contest.getId());

            return new ContestPastResponse(
                    contest.getId(),
                    contest.getTitle(),
                    contest.getThemeImageUrl(),
                    summary != null ? (int) summary.entryCount() : 0,
                    summary != null ? summary.participantCount() : 0,
                    summary != null ? (int) summary.totalVoteCount() : 0,
                    myRankMap.get(contest.getId()),
                    winner != null ? winner.getUser().getNickname() : null
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
    ) {
        User user = getUser(userId);
        Contest contest = getContestById(contestId);
        ContestPhase phase = contest.getPhase(LocalDateTime.now());
        boolean showRanking = canShowCurrentRanking(phase);

        Page<ContestEntry> entries = contestEntryRepository.findAllByContest(
                contest,
                PageRequest.of(page, size, resolveEntrySort(sort))
        );

        List<Long> entryIds = entries.getContent().stream()
                .map(ContestEntry::getId)
                .toList();
        Set<Long> votedEntryIds = entryIds.isEmpty()
                ? Set.of()
                : contestVoteRepository.findVotedEntryIdsByEntryIdsAndUser(entryIds, user)
                .stream()
                .collect(Collectors.toSet());
        Map<Long, Integer> rankMap = showRanking && !entryIds.isEmpty()
                ? contestEntryRepository.findRanksByEntryIds(entryIds)
                .stream()
                .collect(Collectors.toMap(
                        ContestEntryRankSummary::entryId,
                        rankSummary -> (int) rankSummary.rank()
                ))
                : Map.of();

        List<ContestEntryResponse> responses = entries.getContent().stream()
                .map(entry -> toEntryResponse(
                        entry,
                        showRanking,
                        rankMap.get(entry.getId()),
                        votedEntryIds.contains(entry.getId()),
                        isMine(entry, user)
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
    public ContestEntryDetailResponse getContestEntry(Long contestId, Long entryId, Long userId) {
        User user = getUser(userId);
        Contest contest = getContestById(contestId);
        ContestEntry entry = getEntryByContest(contest, entryId);

        ContestPhase phase = contest.getPhase(LocalDateTime.now());
        boolean showRanking = canShowCurrentRanking(phase);
        boolean voted = contestVoteRepository.existsByEntryAndUser(entry, user);
        boolean mine = isMine(entry, user);
        long remainingVoteCount = getRemainingVoteCount(contest, user);

        return ContestEntryDetailResponse.from(
                entry,
                imageStorageService.getPresignedUrl(entry.getPhotoUrl()),
                phase,
                showRanking,
                showRanking ? calculateRank(contest, entry) : null,
                voted,
                mine,
                phase == ContestPhase.VOTING,
                mine && (phase == ContestPhase.SUBMITTING || phase == ContestPhase.VOTING),
                contest.getVoteLimit(),
                remainingVoteCount
        );
    }

    // 출품작 등록
    @Transactional
    public ContestEntryResponse createEntry(
            Long contestId,
            Long userId,
            ContestCreateEntryRequest request,
            MultipartFile photo
    ) {
        User user = getUser(userId);
        Contest contest = getContestById(contestId);
        validatePhase(contest, ContestPhase.SUBMITTING, ContestErrorCode.NOT_SUBMITTING_PERIOD);

        long myEntryCount = contestEntryRepository.countByContestAndUser(contest, user);
        if (myEntryCount >= contest.getMaxEntriesPerUser()) {
            throw new CustomException(ContestErrorCode.ENTRY_LIMIT_EXCEEDED);
        }

        Spot spot = getSpotOrNull(request.spotId());
        ImageUploadResult uploadResult = imageStorageService.upload(photo, "contests/" + contest.getId());

        ContestEntry entry = ContestEntry.create(
                contest,
                user,
                uploadResult.key(),
                request.caption(),
                spot,
                resolveSpotName(request, spot)
        );

        ContestEntry savedEntry = contestEntryRepository.save(entry);
        return toEntryResponse(contest, savedEntry, user, false);
    }

    // 출품작 삭제
    @Transactional
    public void deleteEntry(Long contestId, Long entryId, Long userId) {
        User user = getUser(userId);
        Contest contest = getContestById(contestId);
        ContestEntry entry = getEntryByContest(contest, entryId);

        if (!isMine(entry, user)) {
            throw new CustomException(ContestErrorCode.NOT_MY_ENTRY);
        }

        ContestPhase phase = contest.getPhase(LocalDateTime.now());
        if (phase == ContestPhase.RESULT || phase == ContestPhase.ENDED) {
            throw new CustomException(ContestErrorCode.NOT_SUBMITTING_PERIOD);
        }

        contestVoteRepository.deleteAllByEntry(entry);
        imageStorageService.delete(entry.getPhotoUrl());
        contestEntryRepository.delete(entry);
    }

    // 출품작 투표
    @Transactional
    public ContestVoteResponse voteEntry(Long contestId, Long entryId, Long userId) {
        User user = getUser(userId);
        Contest contest = getContestById(contestId);
        ContestEntry entry = getEntryByContest(contest, entryId);

        validatePhase(contest, ContestPhase.VOTING, ContestErrorCode.NOT_VOTING_PERIOD);

        if (contestVoteRepository.existsByEntryAndUser(entry, user)) {
            throw new CustomException(ContestErrorCode.ALREADY_VOTED);
        }

        long usedVoteCount = contestVoteRepository.countByContestAndUser(contest, user);
        if (usedVoteCount >= contest.getVoteLimit()) {
            throw new CustomException(ContestErrorCode.VOTE_LIMIT_EXCEEDED);
        }

        contestVoteRepository.save(ContestVote.create(contest, entry, user));
        entry.increaseVoteCount();

        long updatedUsedVoteCount = usedVoteCount + 1;
        return toVoteResponse(entry, contest, updatedUsedVoteCount, true);
    }

    // 투표 취소
    @Transactional
    public ContestVoteResponse cancelVote(Long contestId, Long entryId, Long userId) {
        User user = getUser(userId);
        Contest contest = getContestById(contestId);
        ContestEntry entry = getEntryByContest(contest, entryId);

        validatePhase(contest, ContestPhase.VOTING, ContestErrorCode.NOT_VOTING_PERIOD);

        ContestVote vote = contestVoteRepository.findByEntryAndUser(entry, user)
                .orElseThrow(() -> new CustomException(ContestErrorCode.VOTE_NOT_FOUND));

        long usedVoteCount = contestVoteRepository.countByContestAndUser(contest, user);
        contestVoteRepository.delete(vote);
        entry.decreaseVoteCount();

        long updatedUsedVoteCount = Math.max(0, usedVoteCount - 1);
        return toVoteResponse(entry, contest, updatedUsedVoteCount, false);
    }

    // 내 출품 현황 조회
    public ContestMyEntryResponse getMyEntry(Long contestId, Long userId) {
        User user = getUser(userId);
        Contest contest = getContestById(contestId);
        ContestPhase phase = contest.getPhase(LocalDateTime.now());
        boolean showRanking = canShowCurrentRanking(phase);

        List<ContestEntryResponse> entries = contestEntryRepository.findAllByContestAndUser(contest, user).stream()
                .map(entry -> toEntryResponse(contest, entry, user, showRanking))
                .toList();

        int myEntryCount = entries.size();
        return new ContestMyEntryResponse(
                contest.getId(),
                contest.getTitle(),
                phase,
                myEntryCount,
                contest.getMaxEntriesPerUser(),
                Math.max(0, contest.getMaxEntriesPerUser() - myEntryCount),
                entries
        );
    }

    // 내가 투표한 작품 조회
    public ContestMyVoteResponse getMyVotes(Long contestId, Long userId) {
        User user = getUser(userId);
        Contest contest = getContestById(contestId);
        List<ContestVote> votes = contestVoteRepository.findAllByContestAndUser(contest, user);

        List<ContestMyVoteResponse.VotedEntry> votedEntries = votes.stream()
                .map(vote -> {
                    ContestEntry entry = vote.getEntry();
                    return new ContestMyVoteResponse.VotedEntry(
                            entry.getId(),
                            imageStorageService.getPresignedUrl(entry.getPhotoUrl()),
                            entry.getUser().getNickname(),
                            entry.getSpotName(),
                            vote.getCreatedAt()
                    );
                })
                .toList();

        long usedVoteCount = votes.size();
        return new ContestMyVoteResponse(
                contest.getId(),
                contest.getVoteLimit(),
                usedVoteCount,
                Math.max(0, contest.getVoteLimit() - usedVoteCount),
                votedEntries
        );
    }

    // 내 콘테스트 참여 기록 조회
    public ContestMyHistoryResponse getMyHistory(Long userId) {
        User user = getUser(userId);
        List<ContestEntry> entries = contestEntryRepository.findAllByUserOrderByCreatedAtDesc(user);

        List<ContestMyHistoryResponse.HistoryItem> items = entries.stream()
                .map(entry -> {
                    Contest contest = entry.getContest();
                    ContestPhase phase = contest.getPhase(LocalDateTime.now());
                    boolean showRanking = canShowCurrentRanking(phase);
                    Integer rank = showRanking
                            ? calculateRank(contest, entry)
                            : null;

                    return new ContestMyHistoryResponse.HistoryItem(
                            contest.getId(),
                            contest.getTitle(),
                            imageStorageService.getPresignedUrl(entry.getPhotoUrl()),
                            rank,
                            showRanking ? entry.getVoteCount() : null,
                            rank == null ? "PENDING" : "RANKED"
                    );
                })
                .toList();

        Integer bestRank = items.stream()
                .map(ContestMyHistoryResponse.HistoryItem::myRank)
                .filter(rank -> rank != null)
                .min(Integer::compareTo)
                .orElse(null);

        LocalDateTime now = LocalDateTime.now();
        long totalVoteCount = entries.stream()
                .filter(entry -> canShowCurrentRanking(entry.getContest().getPhase(now)))
                .mapToLong(ContestEntry::getVoteCount)
                .sum();

        return new ContestMyHistoryResponse(entries.size(), bestRank, totalVoteCount, items);
    }

    // 순위 집계 조회
    public ContestRankingHistoryResponse getRankingHistory(Long contestId) {
        Contest contest = getContestById(contestId);
        ContestPhase phase = contest.getPhase(LocalDateTime.now());

        if (!canShowRankingHistory(phase)) {
            throw new CustomException(ContestErrorCode.RESULT_NOT_OPENED);
        }

        List<ContestRankingSnapshot> snapshots = rankingSnapshotRepository
                .findAllByContestOrderBySnapshotDateAscRankAsc(contest);

        List<ContestRankingHistoryResponse.Snapshot> responses = snapshots.stream()
                .collect(java.util.stream.Collectors.groupingBy(ContestRankingSnapshot::getSnapshotDate))
                .entrySet()
                .stream()
                .map(entry -> new ContestRankingHistoryResponse.Snapshot(
                        entry.getKey(),
                        true,
                        entry.getValue().stream()
                                .sorted(Comparator.comparingInt(ContestRankingSnapshot::getRank))
                                .map(snapshot -> new ContestRankingHistoryResponse.Ranking(
                                        snapshot.getRank(),
                                        snapshot.getEntry().getId(),
                                        imageStorageService.getPresignedUrl(snapshot.getEntry().getPhotoUrl()),
                                        snapshot.getEntry().getUser().getNickname(),
                                        snapshot.getEntry().getSpotName(),
                                        snapshot.getVoteCount()
                                ))
                                .toList()
                ))
                .sorted(Comparator.comparing(ContestRankingHistoryResponse.Snapshot::snapshotDate))
                .toList();

        return new ContestRankingHistoryResponse(contest.getId(), phase, responses);
    }

    // 콘테스트 결과 조회
    public ContestResultResponse getContestResult(Long contestId, Long userId) {
        User user = getUser(userId);
        Contest contest = getContestById(contestId);

        if (!canShowRanking(contest.getPhase(LocalDateTime.now()))) {
            throw new CustomException(ContestErrorCode.RESULT_NOT_OPENED);
        }

        List<ContestEntry> rankedEntries = findRankedEntries(contest);
        List<ContestResultResponse.ResultEntry> rankings = toResultEntries(contest, rankedEntries, 5);

        ContestEntry winner = rankedEntries.stream().findFirst().orElse(null);
        ContestEntry myBestEntry = contestEntryRepository.findAllByContestAndUser(contest, user).stream()
                .min(Comparator.comparing(entry -> rankOf(rankedEntries, entry)))
                .orElse(null);

        return new ContestResultResponse(
                contest.getId(),
                contest.getTitle(),
                (int) contestEntryRepository.countByContest(contest),
                contestEntryRepository.countDistinctUserByContest(contest),
                (int) contestVoteRepository.countByContest(contest),
                winner != null ? toResultEntry(contest, winner, 1) : null,
                myBestEntry != null ? toResultEntry(contest, myBestEntry, rankOf(rankedEntries, myBestEntry)) : null,
                rankings
        );
    }

    // 다음 콘테스트 알림 구독
    @Transactional
    public ContestSubscriptionResponse subscribeContest(Long contestId, Long userId) {
        User user = getUser(userId);
        Contest contest = getContestById(contestId);

        if (subscriptionRepository.existsByContestAndUser(contest, user)) {
            throw new CustomException(ContestErrorCode.ALREADY_SUBSCRIBED);
        }

        subscriptionRepository.save(ContestSubscription.create(contest, user));
        return new ContestSubscriptionResponse(contest.getId(), true);
    }

    // 다음 콘테스트 알림 구독 취소
    @Transactional
    public ContestSubscriptionResponse unsubscribeContest(Long contestId, Long userId) {
        User user = getUser(userId);
        Contest contest = getContestById(contestId);

        subscriptionRepository.findByContestAndUser(contest, user)
                .ifPresent(subscriptionRepository::delete);

        return new ContestSubscriptionResponse(contest.getId(), false);
    }

    // 출품작 신고
    @Transactional
    public void reportEntry(Long entryId, Long userId, ContestReportRequest request) {
        User user = getUser(userId);
        ContestEntry entry = getEntryById(entryId);

        if (contestReportRepository.existsByEntryAndUser(entry, user)) {
            throw new CustomException(ContestErrorCode.ALREADY_REPORTED);
        }

        contestReportRepository.save(ContestReport.create(entry, user, request.reason(), request.content()));
    }

    private ContestResponse toContestResponse(Contest contest, User user, LocalDateTime now) {
        ContestPhase phase = contest.getPhase(now);
        int entryCount = (int) contestEntryRepository.countByContest(contest);
        long participantCount = contestEntryRepository.countDistinctUserByContest(contest);
        int myEntryCount = (int) contestEntryRepository.countByContestAndUser(contest, user);
        long usedVoteCount = contestVoteRepository.countByContestAndUser(contest, user);

        return ContestResponse.of(contest, phase, entryCount, participantCount, myEntryCount, usedVoteCount);
    }

    private ContestEntryResponse toEntryResponse(
            Contest contest,
            ContestEntry entry,
            User user,
            boolean showRanking
    ) {
        return toEntryResponse(
                contest,
                entry,
                user,
                showRanking,
                contestVoteRepository.existsByEntryAndUser(entry, user)
        );
    }

    private ContestEntryResponse toEntryResponse(
            Contest contest,
            ContestEntry entry,
            User user,
            boolean showRanking,
            boolean voted
    ) {
        return toEntryResponse(
                entry,
                showRanking,
                showRanking ? calculateRank(contest, entry) : null,
                voted,
                isMine(entry, user)
        );
    }

    private ContestEntryResponse toEntryResponse(
            ContestEntry entry,
            boolean showRanking,
            Integer rank,
            boolean voted,
            boolean mine
    ) {
        return ContestEntryResponse.from(
                entry,
                imageStorageService.getPresignedUrl(entry.getPhotoUrl()),
                showRanking,
                rank,
                voted,
                mine
        );
    }

    private ContestVoteResponse toVoteResponse(
            ContestEntry entry,
            Contest contest,
            long usedVoteCount,
            boolean voted
    ) {
        return new ContestVoteResponse(
                entry.getId(),
                voted,
                contest.getVoteLimit(),
                usedVoteCount,
                Math.max(0, contest.getVoteLimit() - usedVoteCount)
        );
    }

    private ContestResultResponse.ResultEntry toResultEntry(Contest contest, ContestEntry entry, int rank) {
        return new ContestResultResponse.ResultEntry(
                rank,
                entry.getId(),
                imageStorageService.getPresignedUrl(entry.getPhotoUrl()),
                entry.getUser().getNickname(),
                entry.getCaption(),
                entry.getSpot() != null ? entry.getSpot().getId() : null,
                entry.getSpotName(),
                entry.getVoteCount()
        );
    }

    private List<ContestResultResponse.ResultEntry> toResultEntries(
            Contest contest,
            List<ContestEntry> rankedEntries,
            int limit
    ) {
        List<ContestResultResponse.ResultEntry> responses = new ArrayList<>();

        for (int i = 0; i < rankedEntries.size() && i < limit; i++) {
            ContestEntry entry = rankedEntries.get(i);
            responses.add(toResultEntry(contest, entry, rankOf(rankedEntries, entry)));
        }

        return responses;
    }

    private Sort resolveEntrySort(String sort) {
        if ("votes".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "voteCount")
                    .and(Sort.by(Sort.Direction.ASC, "createdAt"));
        }

        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    private boolean canShowRanking(ContestPhase phase) {
        return phase == ContestPhase.RESULT || phase == ContestPhase.ENDED;
    }

    private boolean canShowCurrentRanking(ContestPhase phase) {
        return phase == ContestPhase.VOTING || phase == ContestPhase.RESULT || phase == ContestPhase.ENDED;
    }

    private boolean canShowRankingHistory(ContestPhase phase) {
        return phase == ContestPhase.VOTING || phase == ContestPhase.RESULT || phase == ContestPhase.ENDED;
    }

    private Integer calculateRank(Contest contest, ContestEntry targetEntry) {
        int rank = rankOf(findRankedEntries(contest), targetEntry);
        return rank == Integer.MAX_VALUE ? null : rank;
    }

    private int rankOf(List<ContestEntry> entries, ContestEntry targetEntry) {
        Integer targetVoteCount = null;

        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getId().equals(targetEntry.getId())) {
                targetVoteCount = entries.get(i).getVoteCount();
                break;
            }
        }

        if (targetVoteCount == null) {
            return Integer.MAX_VALUE;
        }

        int voteCount = targetVoteCount;
        long higherVoteEntryCount = entries.stream()
                .filter(entry -> entry.getVoteCount() > voteCount)
                .count();

        return (int) higherVoteEntryCount + 1;
    }

    private List<ContestEntry> findRankedEntries(Contest contest) {
        return contestEntryRepository.findAllByContest(contest, Pageable.unpaged())
                .getContent()
                .stream()
                .sorted(
                        Comparator.comparingInt(ContestEntry::getVoteCount).reversed()
                                .thenComparing(ContestEntry::getCreatedAt)
                )
                .toList();
    }

    private Contest getContestById(Long contestId) {
        return contestRepository.findById(contestId)
                .orElseThrow(() -> new CustomException(ContestErrorCode.CONTEST_NOT_FOUND));
    }

    private ContestEntry getEntryById(Long entryId) {
        return contestEntryRepository.findById(entryId)
                .orElseThrow(() -> new CustomException(ContestErrorCode.ENTRY_NOT_FOUND));
    }

    private ContestEntry getEntryByContest(Contest contest, Long entryId) {
        return contestEntryRepository.findByIdAndContest(entryId, contest)
                .orElseThrow(() -> new CustomException(ContestErrorCode.ENTRY_NOT_FOUND));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }

    private Spot getSpotOrNull(Long spotId) {
        if (spotId == null) {
            return null;
        }

        return spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));
    }

    private String resolveSpotName(ContestCreateEntryRequest request, Spot spot) {
        if (spot != null) {
            return spot.getName();
        }

        return request.spotName();
    }

    private boolean isMine(ContestEntry entry, User user) {
        return entry.getUser().getId().equals(user.getId());
    }

    private long getRemainingVoteCount(Contest contest, User user) {
        long usedVoteCount = contestVoteRepository.countByContestAndUser(contest, user);
        return Math.max(0, contest.getVoteLimit() - usedVoteCount);
    }

    private void validatePhase(Contest contest, ContestPhase requiredPhase, ContestErrorCode errorCode) {
        if (contest.getPhase(LocalDateTime.now()) != requiredPhase) {
            throw new CustomException(errorCode);
        }
    }
}
