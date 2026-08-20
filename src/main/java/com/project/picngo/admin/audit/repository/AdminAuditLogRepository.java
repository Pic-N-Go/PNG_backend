package com.project.picngo.admin.audit.repository;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.domain.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    @Query("SELECT a FROM AdminAuditLog a WHERE " +
            "(:adminUserId IS NULL OR a.adminUserId = :adminUserId) AND " +
            "(:actionType IS NULL OR a.actionType = :actionType)")
    Page<AdminAuditLog> searchLogs(
            @Param("adminUserId") Long adminUserId,
            @Param("actionType") AdminActionType actionType,
            Pageable pageable
    );
}
