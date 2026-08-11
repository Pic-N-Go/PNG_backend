package com.project.picngo.contest.repository;

import com.project.picngo.contest.domain.Contest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    // 현재 날짜 기준으로 진행 중인 콘테스트 조회
    // submitStartAt <= now < resultOpenAt
    Optional<Contest> findFirstBySubmitStartAtLessThanEqualAndResultOpenAtGreaterThanOrderBySubmitStartAtDesc(
            LocalDateTime now,
            LocalDateTime sameNow
    );

    // 다음 예저 콘테스트 조회
    Optional<Contest> findFirstBySubmitStartAtAfterOrderBySubmitStartAtAsc(LocalDateTime now);

}
