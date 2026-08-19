package com.project.picngo.contest.repository;

import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestEntry;
import com.project.picngo.contest.domain.ContestVote;
import com.project.picngo.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContestVoteRepository extends JpaRepository<ContestVote, Long> {

    // 콘테스트 기간 내 내가 사용한 투표 수 조회
    long countByContestAndUser(Contest contest, User user);

    // 특정 출품작에 이미 투표했는지 확인
    boolean existsByEntryAndUser(ContestEntry entry, User user);

    // 내가 누른 투표 조회
    Optional<ContestVote> findByEntryAndUser(ContestEntry entry, User user);

    // 내가 투표한 출품작 목록 조회
    @Query("""
            select v
            from ContestVote v
            join fetch v.entry e
            join fetch e.user
            left join fetch e.spot
            where v.contest = :contest
              and v.user = :user
            order by v.createdAt desc
            """)
    List<ContestVote> findAllByContestAndUser(
            @Param("contest") Contest contest,
            @Param("user") User user
    );

    // 콘테스트 전체 투표 내역 조회
    List<ContestVote> findAllByContest(Contest contest);

    // 콘테스트 전체 투표 수 조회
    long countByContest(Contest contest);

    @Query("""
            select v.entry.id
            from ContestVote v
            where v.entry.id in :entryIds
              and v.user = :user
            """)
    List<Long> findVotedEntryIdsByEntryIdsAndUser(
            @Param("entryIds") List<Long> entryIds,
            @Param("user") User user
    );

    // 출품작의 투표 내역 삭제
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from ContestVote v
            where v.entry = :entry
            """)
    void deleteAllByEntry(@Param("entry") ContestEntry entry);
}
