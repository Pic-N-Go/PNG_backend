package com.project.picngo.contest.repository;

import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestRankingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ContestRankingSnapshotRepository extends JpaRepository<ContestRankingSnapshot, Long> {

    // 특정 콘테스트의 집계 기록 조회
    List<ContestRankingSnapshot> findAllByContestOrderBySnapshotDateAscRankAsc(Contest contest);

    // 특정 날짜 집계 기록 조회
    List<ContestRankingSnapshot> findAllByContestAndSnapshotDateOrderByRankAsc(
            Contest contest,
            LocalDate snapshotDate
    );

    // 이미 집계된 날짜인지 확인
    boolean existsByContestAndSnapshotDate(Contest contest, LocalDate snapshotDate);
}
