package com.project.picngo.admin.audit.dto;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.domain.AdminAuditLog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "관리자 감사 로그 응답 DTO")
public record AdminAuditLogResponse(
        @Schema(description = "감사 로그 ID", example = "1")
        Long id,

        @Schema(description = "작업 수행 관리자 ID", example = "1")
        Long adminUserId,

        @Schema(description = "관리자 이메일", example = "admin@picngo.com")
        String adminEmail,

        @Schema(description = "관리자 닉네임", example = "최고관리자")
        String adminNickname,

        @Schema(description = "관리자 작업 유형 (ROLE_UPDATE, INQUIRY_ANSWER, EMBEDDING_RECALCULATE, EMBEDDING_BACKFILL, TOUR_API_SYNC)", example = "ROLE_UPDATE")
        AdminActionType actionType,

        @Schema(description = "작업 유형 한글 설명", example = "회원 권한 변경")
        String actionDescription,

        @Schema(description = "대상 엔티티 (USER, INQUIRY, SPOT, TOUR_API)", example = "USER")
        String targetEntity,

        @Schema(description = "대상 식별자 ID", example = "10")
        String targetId,

        @Schema(description = "작업 상세 내용", example = "회원 [사진가(ID: 10)]의 권한을 USER -> ADMIN 으로 변경")
        String details,

        @Schema(description = "접속 IP 주소", example = "127.0.0.1")
        String ipAddress,

        @Schema(description = "작업 일시")
        LocalDateTime createdAt
) {
    public static AdminAuditLogResponse from(AdminAuditLog log) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getAdminUserId(),
                log.getAdminEmail(),
                log.getAdminNickname(),
                log.getActionType(),
                log.getActionType() != null ? log.getActionType().getDescription() : "",
                log.getTargetEntity(),
                log.getTargetId(),
                log.getDetails(),
                log.getIpAddress(),
                log.getCreatedAt()
        );
    }
}
