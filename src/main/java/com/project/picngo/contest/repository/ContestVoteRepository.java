package com.project.picngo.contest.repository;

import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestEntry;
import com.project.picngo.contest.domain.ContestVote;
import com.project.picngo.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

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
    List<ContestVote> findAllByContestAndUser(Contest contest, User user);

    // 출품작의 투표 내역 삭제
    void deleteAllByEntry(ContestEntry entry);
}
