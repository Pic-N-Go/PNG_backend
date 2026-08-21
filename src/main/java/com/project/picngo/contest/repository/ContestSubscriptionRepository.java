package com.project.picngo.contest.repository;

import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestSubscription;
import com.project.picngo.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContestSubscriptionRepository extends JpaRepository<ContestSubscription, Long> {

    //알림 구독 여부 확인
    boolean existsByContestAndUser(Contest contest, User user);

    //알림 구독 내역 확인
    Optional<ContestSubscription> findByContestAndUser(Contest contest, User user);

}
