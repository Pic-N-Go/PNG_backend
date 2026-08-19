package com.project.picngo.admin.audit.service;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.domain.AdminAuditLog;
import com.project.picngo.admin.audit.dto.AdminAuditLogResponse;
import com.project.picngo.admin.audit.repository.AdminAuditLogRepository;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final UserRepository userRepository;

    /**
     * 관리자 액션 감사 로그 기록 (독립 트랜잭션으로 커밋되어 본 작업 실패와 무관하게 기록 가능)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long adminUserId, AdminActionType actionType, String targetEntity, String targetId, String details, String ipAddress) {
        String adminEmail = null;
        String adminNickname = null;

        if (adminUserId != null) {
            User admin = userRepository.findById(adminUserId).orElse(null);
            if (admin != null) {
                adminEmail = admin.getEmail();
                adminNickname = admin.getNickname();
            }
        }

        AdminAuditLog auditLog = AdminAuditLog.create(
                adminUserId,
                adminEmail,
                adminNickname,
                actionType,
                targetEntity,
                targetId,
                details,
                ipAddress
        );

        adminAuditLogRepository.save(auditLog);
        log.info("🛡️ [Admin Audit Log] adminUserId={}, action={}, target={}:{}, details={}",
                adminUserId, actionType, targetEntity, targetId, details);
    }

    /**
     * 감사 로그 목록 페이징 및 필터 검색
     */
    public Page<AdminAuditLogResponse> getAuditLogs(Long adminUserId, AdminActionType actionType, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminAuditLog> logs = adminAuditLogRepository.searchLogs(adminUserId, actionType, pageable);
        return logs.map(AdminAuditLogResponse::from);
    }
}
