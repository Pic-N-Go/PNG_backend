package com.project.picngo.contest.repository;

import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestEntry;
import com.project.picngo.contest.dto.ContestEntryRankSummary;
import com.project.picngo.contest.dto.ContestMyRankSummary;
import com.project.picngo.contest.dto.ContestPastSummary;
import com.project.picngo.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContestEntryRepository extends JpaRepository<ContestEntry, Long> {

    // 콘테스트별 출품작 목록 조회
    @EntityGraph(attributePaths = {"user", "spot"})
    Page<ContestEntry> findAllByContest(Contest contest, Pageable pageable);

    // 콘테스트별 내 출품작 목록 조회
    List<ContestEntry> findAllByContestAndUser(Contest contest, User user);

    // 내가 출품한 전체 출품작 목록 조회
    List<ContestEntry> findAllByUserOrderByCreatedAtDesc(User user);

    // 콘테스트별 내 출품작 수 조회
    long countByContestAndUser(Contest contest, User user);

    // 콘테스트별 전체 출품작 수 조회
    long countByContest(Contest contest);

    // 콘테스트별 참여자 수 조회
    long countDistinctUserByContest(Contest contest);

    // 특정 콘테스트에 속한 출품작 조회
    Optional<ContestEntry> findByIdAndContest(Long id, Contest contest);

    @Query("""
            select new com.project.picngo.contest.dto.ContestPastSummary(
                e.contest.id,
                count(e),
                count(distinct e.user.id),
                coalesce(sum(e.voteCount), 0)
            )
            from ContestEntry e
            where e.contest.id in :contestIds
            group by e.contest.id
            """)
    List<ContestPastSummary> findPastSummariesByContestIds(@Param("contestIds") List<Long> contestIds);

    @Query("""
            select e
            from ContestEntry e
            join fetch e.user
            where e.contest.id in :contestIds
              and not exists (
                  select 1
                  from ContestEntry other
                  where other.contest = e.contest
                    and (
                        other.voteCount > e.voteCount
                        or (other.voteCount = e.voteCount and other.createdAt < e.createdAt)
                    )
              )
            """)
    List<ContestEntry> findWinnersByContestIds(@Param("contestIds") List<Long> contestIds);

    @Query("""
            select new com.project.picngo.contest.dto.ContestMyRankSummary(
                e.contest.id,
                (
                    select count(higher) + 1
                    from ContestEntry higher
                    where higher.contest = e.contest
                      and higher.voteCount > e.voteCount
                )
            )
            from ContestEntry e
            where e.contest.id in :contestIds
              and e.user = :user
            """)
    List<ContestMyRankSummary> findMyRanksByContestIds(
            @Param("contestIds") List<Long> contestIds,
            @Param("user") User user
    );

    @Query("""
            select new com.project.picngo.contest.dto.ContestEntryRankSummary(
                e.id,
                (
                    select count(higher) + 1
                    from ContestEntry higher
                    where higher.contest = e.contest
                      and higher.voteCount > e.voteCount
                )
            )
            from ContestEntry e
            where e.id in :entryIds
            """)
    List<ContestEntryRankSummary> findRanksByEntryIds(@Param("entryIds") List<Long> entryIds);
}
