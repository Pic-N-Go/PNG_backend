package com.project.picngo.contest.repository;

import com.project.picngo.contest.domain.Contest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // 가장 나중에 끝나는 회차 (새 회차를 그 뒤에 이어 붙이려고 본다)
    Optional<Contest> findFirstByOrderByResultOpenAtDesc();

    // 지난 콘테스트 목록 조회
    Page<Contest> findAllByResultOpenAtBeforeOrderByResultOpenAtDesc(LocalDateTime now, Pageable pageable);

    // 투표 기간 중인 콘테스트 조회
    List<Contest> findAllByVoteStartAtLessThanEqualAndVoteEndAtGreaterThan(
            LocalDateTime now,
            LocalDateTime sameNow
    );

    // 시작 시점이 도래했으나 아직 알림이 발송되지 않은 콘테스트 조회
    List<Contest> findAllByActiveTrueAndStartNotificationSentFalseAndSubmitStartAtLessThanEqual(LocalDateTime now);

    // 결과 발표 시점이 도래했으나 아직 알림이 발송되지 않은 콘테스트 조회
    List<Contest> findAllByActiveTrueAndResultNotificationSentFalseAndResultOpenAtLessThanEqual(LocalDateTime now);

    // 관리자용 전체 콘테스트 목록 페이징 (최신 등록순)
    Page<Contest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 특정 콘테스트를 제외하고, 지정된 기간과 겹치는 활성 콘테스트가 존재하는지 확인
    @Query("""
            select count(c) > 0
            from Contest c
            where c.id != :contestId
              and c.active = true
              and c.submitStartAt < :newResultOpenAt
              and :newSubmitStartAt < c.resultOpenAt
            """)
    boolean existsOverlappingContest(
            @Param("contestId") Long contestId,
            @Param("newSubmitStartAt") LocalDateTime newSubmitStartAt,
            @Param("newResultOpenAt") LocalDateTime newResultOpenAt
    );
}
