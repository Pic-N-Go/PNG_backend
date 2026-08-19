package com.project.picngo.admin.audit.controller;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.dto.AdminAuditLogResponse;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 감사 로그(Audit Log) 조회 컨트롤러.
 * /admin/** 경로는 SecurityConfig에 의해 ROLE_ADMIN 권한이 필수입니다.
 */
@RestController
@RequestMapping("/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController implements AdminAuditLogControllerApiSpec {

    private final AdminAuditLogService adminAuditLogService;

    @Override
    @GetMapping
    public ResponseEntity<Page<AdminAuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) Long adminUserId,
            @RequestParam(required = false) AdminActionType actionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminAuditLogService.getAuditLogs(adminUserId, actionType, page, size));
    }
}
