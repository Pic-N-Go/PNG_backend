package com.project.picngo.contest.repository;

import com.project.picngo.contest.domain.ContestEntry;
import com.project.picngo.contest.domain.ContestReport;
import com.project.picngo.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestReportRepository extends JpaRepository<ContestReport, Long> {

    //같은 출품작을 이미 신고했는지 확인
    boolean existsByEntryAndUser(ContestEntry entry, User user);
}
