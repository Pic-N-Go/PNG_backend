package com.project.picngo.report.dto;

import com.project.picngo.report.domain.Report;
import com.project.picngo.report.domain.ReportReason;
import com.project.picngo.report.domain.ReportStatus;
import com.project.picngo.report.domain.ReportTargetType;

import java.time.LocalDateTime;

public record AdminReportDetailResponse(
        Long reportId,
        ReportTargetType targetType,
        Long targetId,
        ReportReason reason,
        String detail,
        String targetContentSnapshot,
        ReportStatus status,
        Long reporterId,
        String reporterNickname,
        Long reportedUserId,
        String reportedUserNickname,
        LocalDateTime createdAt,
        Long handledById,
        String handledByNickname,
        LocalDateTime handledAt,
        String resolutionNote
) {
    public static AdminReportDetailResponse from(Report report) {
        return new AdminReportDetailResponse(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getDetail(),
                report.getTargetContentSnapshot(),
                report.getStatus(),
                report.getReporter().getId(),
                report.getReporter().getNickname(),
                report.getReportedUser().getId(),
                report.getReportedUser().getNickname(),
                report.getCreatedAt(),
                report.getHandledBy() == null ? null : report.getHandledBy().getId(),
                report.getHandledBy() == null ? null : report.getHandledBy().getNickname(),
                report.getHandledAt(),
                report.getResolutionNote()
        );
    }
}
