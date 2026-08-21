package com.project.picngo.contest.repository;

import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestRankingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ContestRankingSnapshotRepository extends JpaRepository<ContestRankingSnapshot, Long> {

    // 특정 콘테스트의 집계 기록 조회
    @Query("""
            select snapshot
            from ContestRankingSnapshot snapshot
            join fetch snapshot.entry entry
            join fetch entry.user
            left join fetch entry.spot
            where snapshot.contest = :contest
            order by snapshot.snapshotDate asc, snapshot.rank asc
            """)
    List<ContestRankingSnapshot> findAllByContestOrderBySnapshotDateAscRankAsc(@Param("contest") Contest contest);

    // 특정 날짜 집계 기록 조회
    List<ContestRankingSnapshot> findAllByContestAndSnapshotDateOrderByRankAsc(
            Contest contest,
            LocalDate snapshotDate
    );

    // 이미 집계된 날짜인지 확인
    boolean existsByContestAndSnapshotDate(Contest contest, LocalDate snapshotDate);
}
