package com.project.picngo.contest.repository;

import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestSubscription;
import com.project.picngo.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ContestSubscriptionRepository extends JpaRepository<ContestSubscription, Long> {

    //알림 구독 여부 확인
    boolean existsByContestAndUser(Contest contest, User user);

    //알림 구독 내역 확인
    Optional<ContestSubscription> findByContestAndUser(Contest contest, User user);

    // 이미 있으면 조용히 넘어간다. save + 제약 위반 catch로는 트랜잭션이 rollback-only로 찍혀
    // 커밋에서 UnexpectedRollbackException이 나므로, 중복 판정을 DB에 맡긴다.
    @Modifying
    @Query(value = "INSERT IGNORE INTO contest_subscription (contest_id, user_id, created_at) "
            + "VALUES (:contestId, :userId, :createdAt)", nativeQuery = true)
    void insertIgnore(@Param("contestId") Long contestId,
                      @Param("userId") Long userId,
                      @Param("createdAt") LocalDateTime createdAt);

}
