package com.project.picngo.contest.repository;

import com.project.picngo.contest.domain.ContestEntry;
import com.project.picngo.contest.domain.ContestReport;
import com.project.picngo.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContestReportRepository extends JpaRepository<ContestReport, Long> {

    //같은 출품작을 이미 신고했는지 확인
    boolean existsByEntryAndUser(ContestEntry entry, User user);

    //관리자용 전체 신고 목록 페이징 (최신순)
    Page<ContestReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    //출품작 삭제 시 관련 신고 데이터 일괄 삭제
    @Modifying
    @Query("delete from ContestReport r where r.entry = :entry")
    void deleteAllByEntry(@Param("entry") ContestEntry entry);

    //출품작 ID 목록별 신고 건수 집계
    @Query("select r.entry.id, count(r) from ContestReport r where r.entry.id in :entryIds group by r.entry.id")
    List<Object[]> countReportsByEntryIds(@Param("entryIds") List<Long> entryIds);
}
