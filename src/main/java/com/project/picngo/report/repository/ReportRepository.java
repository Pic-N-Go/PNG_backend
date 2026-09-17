package com.project.picngo.report.repository;

import com.project.picngo.report.domain.Report;
import com.project.picngo.report.domain.ReportStatus;
import com.project.picngo.report.domain.ReportTargetType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporter_IdAndTargetTypeAndTargetId(
            Long reporterId,
            ReportTargetType targetType,
            Long targetId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from Report report where report.id = :reportId")
    Optional<Report> findByIdForUpdate(@Param("reportId") Long reportId);

    @EntityGraph(attributePaths = {"reporter", "reportedUser", "handledBy"})
    @Query("select report from Report report where report.id = :reportId")
    Optional<Report> findDetailById(@Param("reportId") Long reportId);

    @EntityGraph(attributePaths = {"reporter", "reportedUser", "handledBy"})
    @Query("select report from Report report")
    Page<Report> findAllForAdmin(Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "reportedUser", "handledBy"})
    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "reporter")
    @Query("""
            select report from Report report
            where report.targetType = :targetType
              and report.targetId = :targetId
              and report.status = :status
            order by report.id
            """)
    List<Report> findByTargetAndStatusForUpdate(
            @Param("targetType") ReportTargetType targetType,
            @Param("targetId") Long targetId,
            @Param("status") ReportStatus status
    );
}
