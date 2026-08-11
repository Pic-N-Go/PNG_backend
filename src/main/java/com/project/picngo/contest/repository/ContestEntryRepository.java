package com.project.picngo.contest.repository;

import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestEntry;
import com.project.picngo.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContestEntryRepository extends JpaRepository<ContestEntry, Long> {

    // 콘테스트별 출품작 목록 조회
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
}
