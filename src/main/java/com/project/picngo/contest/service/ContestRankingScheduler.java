package com.project.picngo.contest.service;

import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestEntry;
import com.project.picngo.contest.domain.ContestRankingSnapshot;
import com.project.picngo.contest.repository.ContestEntryRepository;
import com.project.picngo.contest.repository.ContestRankingSnapshotRepository;
import com.project.picngo.contest.repository.ContestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ContestRankingScheduler {

    private static final int SNAPSHOT_LIMIT = 3;

    private final ContestRepository contestRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final ContestRankingSnapshotRepository rankingSnapshotRepository;

    // 매일 자정에 투표 기간 콘테스트 순위 집계
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void createDailyRankingSnapshot() {
        // @Scheduled(zone)은 언제 도는지만 정한다 — 여기서 읽는 시각은 JVM 기본 존이라
        // 컨테이너가 UTC면 날짜가 하루 어긋난 스냅샷이 쌓인다
        LocalDateTime now = LocalDateTime.now(Contest.ZONE);
        LocalDate snapshotDate = LocalDate.now(Contest.ZONE);

        contestRepository.findAllByVoteStartAtLessThanEqualAndVoteEndAtGreaterThan(now, now).stream()
                .forEach(contest -> createSnapshotIfAbsent(contest, snapshotDate));
    }

    private void createSnapshotIfAbsent(Contest contest, LocalDate snapshotDate) {
        if (rankingSnapshotRepository.existsByContestAndSnapshotDate(contest, snapshotDate)) {
            return;
        }

        List<ContestEntry> topEntries = contestEntryRepository.findAllByContest(
                contest,
                PageRequest.of(
                        0,
                        SNAPSHOT_LIMIT,
                        Sort.by(Sort.Direction.DESC, "voteCount")
                                .and(Sort.by(Sort.Direction.ASC, "createdAt"))
                )
        ).getContent();

        for (int i = 0; i < topEntries.size(); i++) {
            ContestEntry entry = topEntries.get(i);
            rankingSnapshotRepository.save(
                    ContestRankingSnapshot.create(
                            contest,
                            entry,
                            snapshotDate,
                            calculateRank(topEntries, entry),
                            entry.getVoteCount()
                    )
            );
        }
    }

    private int calculateRank(List<ContestEntry> entries, ContestEntry targetEntry) {
        long higherVoteEntryCount = entries.stream()
                .filter(entry -> entry.getVoteCount() > targetEntry.getVoteCount())
                .count();

        return (int) higherVoteEntryCount + 1;
    }
}
