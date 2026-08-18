package com.project.picngo.admin.audit.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자(Admin)의 주요 작업 이력을 영구 보관하는 감사 로그(Audit Log) 엔티티.
 */
@Getter
@Entity
@Table(name = "admin_audit_logs", indexes = {
        @Index(name = "idx_admin_audit_user_id", columnList = "adminUserId"),
        @Index(name = "idx_admin_audit_action_type", columnList = "actionType"),
        @Index(name = "idx_admin_audit_created_at", columnList = "createdAt")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long adminUserId;

    @Column(length = 100)
    private String adminEmail;

    @Column(length = 50)
    private String adminNickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AdminActionType actionType;

    @Column(length = 50)
    private String targetEntity;

    @Column(length = 50)
    private String targetId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(length = 45)
    private String ipAddress;

    @Builder
    private AdminAuditLog(Long adminUserId, String adminEmail, String adminNickname,
                          AdminActionType actionType, String targetEntity, String targetId,
                          String details, String ipAddress) {
        this.adminUserId = adminUserId;
        this.adminEmail = adminEmail;
        this.adminNickname = adminNickname;
        this.actionType = actionType;
        this.targetEntity = targetEntity;
        this.targetId = targetId;
        this.details = details;
        this.ipAddress = ipAddress;
    }

    public static AdminAuditLog create(Long adminUserId, String adminEmail, String adminNickname,
                                       AdminActionType actionType, String targetEntity, String targetId,
                                       String details, String ipAddress) {
        return AdminAuditLog.builder()
                .adminUserId(adminUserId)
                .adminEmail(adminEmail)
                .adminNickname(adminNickname)
                .actionType(actionType)
                .targetEntity(targetEntity)
                .targetId(targetId)
                .details(details)
                .ipAddress(ipAddress)
                .build();
    }
}
