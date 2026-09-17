package com.project.picngo.report.dto;

import com.project.picngo.report.domain.Report;
import com.project.picngo.report.domain.ReportReason;
import com.project.picngo.report.domain.ReportStatus;
import com.project.picngo.report.domain.ReportTargetType;

import java.time.LocalDateTime;

public record AdminReportListResponse(
        Long reportId,
        ReportTargetType targetType,
        Long targetId,
        ReportReason reason,
        ReportStatus status,
        Long reporterId,
        String reporterNickname,
        Long reportedUserId,
        String reportedUserNickname,
        LocalDateTime createdAt,
        Long handledById,
        LocalDateTime handledAt
) {
    public static AdminReportListResponse from(Report report) {
        return new AdminReportListResponse(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getStatus(),
                report.getReporter().getId(),
                report.getReporter().getNickname(),
                report.getReportedUser().getId(),
                report.getReportedUser().getNickname(),
                report.getCreatedAt(),
                report.getHandledBy() == null ? null : report.getHandledBy().getId(),
                report.getHandledAt()
        );
    }
}
