package com.project.picngo.contest.repository;

import com.project.picngo.contest.domain.Contest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    // 현재 진행 중인 콘테스트 조회
    Optional<Contest> findFirstBySubmitStartAtLessThanEqualAndResultOpenAtGreaterThanOrderBySubmitStartAtDesc(
            LocalDateTime now,
            LocalDateTime sameNow
    );

    // 다음 예정 콘테스트 조회
    Optional<Contest> findFirstBySubmitStartAtAfterOrderBySubmitStartAtAsc(LocalDateTime now);

    // 지난 콘테스트 목록 조회
    Page<Contest> findAllByResultOpenAtBeforeOrderByResultOpenAtDesc(LocalDateTime now, Pageable pageable);

    // 투표 기간 중인 콘테스트 조회
    List<Contest> findAllByVoteStartAtLessThanEqualAndVoteEndAtGreaterThan(
            LocalDateTime now,
            LocalDateTime sameNow
    );
}
