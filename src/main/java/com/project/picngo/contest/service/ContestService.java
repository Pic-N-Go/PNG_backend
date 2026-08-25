package com.project.picngo.contest.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ContestErrorCode;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.common.image.dto.ImageUploadResult;
import com.project.picngo.common.image.service.ExifExtractor;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestEntry;
import com.project.picngo.contest.domain.ContestPhase;
import com.project.picngo.contest.domain.ContestRankingSnapshot;
import com.project.picngo.contest.domain.ContestReport;
import com.project.picngo.contest.domain.ContestSubscription;
import com.project.picngo.contest.domain.ContestVote;
import com.project.picngo.contest.dto.ContestCreateEntryRequest;
import com.project.picngo.contest.dto.ContestCreateRequest;
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
import org.springframework.dao.DataIntegrityViolationException;
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
    private final ExifExtractor exifExtractor;

    // 프론트 UI가 "3장 출품 · 3표"로 고정돼 있다. 회차마다 다르게 줄 이유가 아직 없어 기본값으로 둔다.
    private static final int DEFAULT_MAX_ENTRIES_PER_USER = 3;
    private static final int DEFAULT_VOTE_LIMIT = 3;

    // 현재 진행 중인 콘테스트 조회
    public ContestResponse getCurrentContest(Long userId) {
        User user = getUser(userId);
        LocalDateTime now = LocalDateTime.now(Contest.ZONE);

        Contest contest = contestRepository
                .findFirstBySubmitStartAtLessThanEqualAndResultOpenAtGreaterThanOrderBySubmitStartAtDesc(now, now)
                .orElseThrow(() -> new CustomException(ContestErrorCode.CURRENT_CONTEST_NOT_FOUND));

        return toContestResponse(contest, user, now);
    }

    // 콘테스트 회차 개설 (관리자)
    //
    // 기간은 받지 않는다 — 출품 2주·투표 2주·투표 종료 익일 09:00 발표는 Contest.create()의 규칙이고
    // 사람이 정하는 건 테마뿐이다. 손으로 INSERT하던 때는 이 규칙을 지킬 방법이 없었다.
    @Transactional
    public ContestResponse createContest(Long userId, ContestCreateRequest request) {
        User user = getUser(userId);
        LocalDateTime now = LocalDateTime.now(Contest.ZONE);

        // 직전 회차의 발표 시각에 이어 붙인다. 겹치게 두면 집계 중 구간에서
        // getCurrentContest가 새 회차를 골라 발표 대기 중인 직전 회차가 어디에서도 안 잡힌다.
        LocalDateTime lastResultOpenAt = contestRepository.findFirstByOrderByResultOpenAtDesc()
                .map(Contest::getResultOpenAt)
                .orElse(null);

        LocalDateTime submitStartAt = request.submitStartAt() != null
                ? request.submitStartAt()
                : (lastResultOpenAt != null && lastResultOpenAt.isAfter(now) ? lastResultOpenAt : now);

        if (lastResultOpenAt != null && submitStartAt.isBefore(lastResultOpenAt)) {
            throw new CustomException(ContestErrorCode.CONTEST_PERIOD_OVERLAP);
        }

        Contest contest = contestRepository.save(Contest.create(
                request.title(),
                request.description(),
                request.themeImageUrl(),
                submitStartAt,
                request.maxEntriesPerUser() != null ? request.maxEntriesPerUser() : DEFAULT_MAX_ENTRIES_PER_USER,
                request.voteLimit() != null ? request.voteLimit() : DEFAULT_VOTE_LIMIT
        ));

        return toContestResponse(contest, user, now);
    }

    // 다음 예정 콘테스트 조회 (없으면 null)
    //
    // 진행 중인 콘테스트가 없을 때 커뮤니티 탭이 "다음 회차 예고 + 알림 신청"을 그린다.
    // getCurrentContest는 submitStartAt <= now 인 것만 찾으므로 아직 시작 전인 회차를 잡지 못한다.
    public ContestResponse getUpcomingContest(Long userId) {
        User user = getUser(userId);
        LocalDateTime now = LocalDateTime.now(Contest.ZONE);

        return contestRepository.findFirstBySubmitStartAtAfterOrderBySubmitStartAtAsc(now)
                .map(contest -> toContestResponse(contest, user, now))
                .orElse(null);
    }

    // 콘테스트 상세 조회
    public ContestResponse getContest(Long contestId, Long userId) {
        User user = getUser(userId);
        Contest contest = getContestById(contestId);

        return toContestResponse(contest, user, LocalDateTime.now(Contest.ZONE));
    }

    // 지난 콘테스트 목록 조회
    public ContestPastPageResponse getPastContests(Long userId, Pageable pageable) {
        User user = getUser(userId);
        Page<Contest> contests = contestRepository.findAllByResultOpenAtBeforeOrderByResultOpenAtDesc(
                LocalDateTime.now(Contest.ZONE),
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
                    imageStorageService.getPresignedUrl(contest.getThemeImageUrl()),
                    contest.getSubmitStartAt(),
                    contest.getResultOpenAt(),
                    summary != null ? (int) summary.entryCount() : 0,
                    summary != null ? summary.participantCount() : 0,
                    summary != null ? (int) summary.totalVoteCount() : 0,
                    myRankMap.get(contest.getId()),
                    winner != null ? winner.getUser().getNickname() : null,
                    winner != null ? imageStorageService.getPresignedUrl(winner.getPhotoUrl()) : null
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
        ContestPhase phase = contest.getPhase(LocalDateTime.now(Contest.ZONE));
        boolean showRanking = canShowRanking(phase);

        Page<ContestEntry> entries = contestEntryRepository.findAllByContest(
                contest,
                PageRequest.of(page, size, resolveEntrySort(sort, phase))
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

        ContestPhase phase = contest.getPhase(LocalDateTime.now(Contest.ZONE));
        boolean showRanking = canShowRanking(phase);
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
                // 내 작품에는 투표할 수 없다 — voteEntry가 거절하므로 여기서도 false로 내려
                // 클라이언트가 이 값만 보고 버튼 상태를 정할 수 있게 한다
                phase == ContestPhase.VOTING && !mine,
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

        // 클라이언트가 원본에서 읽어 보낸 값이 우선이다. 피커가 재인코딩하면서 메타데이터를
        // 떨어뜨리는 경우가 많아, 업로드된 바이트에서 뽑는 건 폴백으로만 쓴다.
        // 둘 다 없으면 null이고(스크린샷·편집본), 그건 정상 경로다.
        LocalDateTime shotAt = request.shotAt() != null
                ? request.shotAt()
                : exifExtractor.extract(photo).takenAt();

        ContestEntry entry = ContestEntry.create(
                contest,
                user,
                uploadResult.key(),
                request.caption(),
                spot,
                resolveSpotName(request, spot),
                shotAt
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

        ContestPhase phase = contest.getPhase(LocalDateTime.now(Contest.ZONE));
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

        // 1인 3표라 자기 작품에 한 표씩만 줘도 순위가 그만큼 왜곡된다
        if (isMine(entry, user)) {
            throw new CustomException(ContestErrorCode.CANNOT_VOTE_OWN_ENTRY);
        }

        if (contestVoteRepository.existsByEntryAndUser(entry, user)) {
            throw new CustomException(ContestErrorCode.ALREADY_VOTED);
        }

        long usedVoteCount = contestVoteRepository.countByContestAndUser(contest, user);
        if (usedVoteCount >= contest.getVoteLimit()) {
            throw new CustomException(ContestErrorCode.VOTE_LIMIT_EXCEEDED);
        }

        contestVoteRepository.save(ContestVote.create(contest, entry, user));
        contestEntryRepository.increaseVoteCount(entry.getId());
        ContestEntry updatedEntry = getEntryByContest(contest, entryId);

        long updatedUsedVoteCount = usedVoteCount + 1;
        return toVoteResponse(updatedEntry, contest, updatedUsedVoteCount, true);
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
        contestEntryRepository.decreaseVoteCount(entry.getId());
        ContestEntry updatedEntry = getEntryByContest(contest, entryId);

        long updatedUsedVoteCount = Math.max(0, usedVoteCount - 1);
        return toVoteResponse(updatedEntry, contest, updatedUsedVoteCount, false);
    }

    // 내 출품 현황 조회
    public ContestMyEntryResponse getMyEntry(Long contestId, Long userId) {
        User user = getUser(userId);
        Contest contest = getContestById(contestId);
        ContestPhase phase = contest.getPhase(LocalDateTime.now(Contest.ZONE));
        boolean showRanking = canShowRanking(phase);

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
                    ContestPhase phase = contest.getPhase(LocalDateTime.now(Contest.ZONE));
                    boolean showRanking = canShowRanking(phase);
                    Integer rank = showRanking
                            ? calculateRank(contest, entry)
                            : null;

                    return new ContestMyHistoryResponse.HistoryItem(
                            contest.getId(),
                            contest.getTitle(),
                            contest.getSubmitStartAt(),
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

        LocalDateTime now = LocalDateTime.now(Contest.ZONE);
        long totalVoteCount = entries.stream()
                .filter(entry -> canShowRanking(entry.getContest().getPhase(now)))
                .mapToLong(ContestEntry::getVoteCount)
                .sum();

        return new ContestMyHistoryResponse(entries.size(), bestRank, totalVoteCount, items);
    }

    // 순위 집계 조회
    public ContestRankingHistoryResponse getRankingHistory(Long contestId) {
        Contest contest = getContestById(contestId);
        ContestPhase phase = contest.getPhase(LocalDateTime.now(Contest.ZONE));

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
                                        // 직접 올린 사진은 S3 objectKey라 presign이 필요하다. 소셜 사진은
                                        // 외부 URL인데 getPresignedUrl이 http로 시작하면 그대로 통과시킨다.
                                        imageStorageService.getPresignedUrl(
                                                snapshot.getEntry().getUser().getDisplayProfileImage()
                                        ),
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

        if (!canShowRanking(contest.getPhase(LocalDateTime.now(Contest.ZONE)))) {
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
                contest.getSubmitStartAt(),
                contest.getVoteEndAt(),
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

        // 멱등하게 둔다 — 프론트에서 탭 한 번으로 켜고 끄는 토글이라
        // 연타나 상태 어긋남이 그대로 409로 튀면 사용자가 볼 이유가 없는 에러가 뜬다.
        //
        // exists 검사만으로는 부족하다. 연타하면 두 요청이 같이 통과해 둘 다 save하고,
        // uk_contest_subscription_user_contest에 걸려 1062 → 400("이미 사용 중인 값입니다")이 된다.
        // 막으려던 바로 그 상황이라 제약 위반까지 성공으로 흡수한다
        // (BookmarkCollectionService.addSpot과 같은 방식).
        if (!subscriptionRepository.existsByContestAndUser(contest, user)) {
            try {
                subscriptionRepository.saveAndFlush(ContestSubscription.create(contest, user));
            } catch (DataIntegrityViolationException e) {
                // 다른 요청이 먼저 넣었다 — 결과는 구독 중으로 같다
            }
        }

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
        boolean subscribed = subscriptionRepository.existsByContestAndUser(contest, user);

        return ContestResponse.of(
                contest,
                phase,
                imageStorageService.getPresignedUrl(contest.getThemeImageUrl()),
                entryCount,
                participantCount,
                myEntryCount,
                usedVoteCount,
                subscribed
        );
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
                entry.getUser().getId(),
                entry.getUser().getNickname(),
                // 직접 올린 사진은 S3 objectKey라 presign이 필요하다. 소셜 사진은 외부 URL인데
                // getPresignedUrl이 http로 시작하면 그대로 통과시킨다.
                imageStorageService.getPresignedUrl(entry.getUser().getDisplayProfileImage()),
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

    /**
     * 목록 정렬.
     *
     * 득표순은 숫자를 가려도 순서 자체가 순위다. 투표 기간에는 그게 의도된 노출이지만
     * (목업이 득표순 칩을 두고 상위 3개는 표수까지 공개한다), 집계 중에는 다르다 —
     * 투표가 끝난 뒤의 순서는 곧 최종 결과라, 열어두면 /result를 발표 시각까지 막아둔 게
     * 아무 의미가 없어진다. 그래서 그 구간만 최신순으로 내린다.
     *
     * 409로 거절하지 않고 조용히 내리는 이유는, 에러로 답하면 "정렬이 되나 안 되나"로
     * 결과 공개 여부를 다시 알려주는 꼴이기 때문이다.
     */
    // 인스턴스 상태를 쓰지 않으므로 static — 목 없이 phase별 정렬만 테스트한다
    static Sort resolveEntrySort(String sort, ContestPhase phase) {
        boolean voteOrderPublic = phase == ContestPhase.VOTING || phase == ContestPhase.ENDED;

        if ("votes".equalsIgnoreCase(sort) && voteOrderPublic) {
            return Sort.by(Sort.Direction.DESC, "voteCount")
                    .and(Sort.by(Sort.Direction.ASC, "createdAt"));
        }

        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    /**
     * 개별 출품작의 순위·득표수를 공개해도 되는지.
     *
     * 결과 발표(resultOpenAt = 투표 종료 다음 날 09:00) 이후에만 참이다.
     * RESULT는 그 전까지의 "집계 중" 구간이라 여기에 넣으면 안 된다 —
     * 넣으면 투표가 끝나는 순간 결과가 열려 발표 시각이 아무 의미가 없어진다.
     *
     * 투표 기간에 공개되는 서열은 두 가지다 — 순위 변동 스냅샷(상위 3개, 표수 포함)과
     * 득표순 정렬의 순서. 둘 다 목업이 의도한 노출이다. 이 함수가 가리는 건 개별 작품에
     * 붙는 숫자(순위·득표수)이고, 내 작품도 예외가 아니다
     * (프론트 "내 출품" 탭도 이 구간에는 순위 자리에 "집계 중"을 띄운다).
     */
    // 인스턴스 상태를 쓰지 않으므로 static — 목 없이 phase 매트릭스만 테스트한다
    // (ReviewService.countUploadable과 같은 방식).
    static boolean canShowRanking(ContestPhase phase) {
        return phase == ContestPhase.ENDED;
    }

    static boolean canShowRankingHistory(ContestPhase phase) {
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
        if (contest.getPhase(LocalDateTime.now(Contest.ZONE)) != requiredPhase) {
            throw new CustomException(errorCode);
        }
    }
}
