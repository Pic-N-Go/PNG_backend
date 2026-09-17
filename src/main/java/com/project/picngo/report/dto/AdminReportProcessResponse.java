package com.project.picngo.report.dto;

import com.project.picngo.report.domain.Report;
import com.project.picngo.report.domain.ReportStatus;

import java.time.LocalDateTime;

public record AdminReportProcessResponse(
        Long reportId,
        ReportStatus status,
        Long handledById,
        LocalDateTime handledAt,
        String resolutionNote
) {
    public static AdminReportProcessResponse from(Report report) {
        return new AdminReportProcessResponse(
                report.getId(),
                report.getStatus(),
                report.getHandledBy().getId(),
                report.getHandledAt(),
                report.getResolutionNote()
        );
    }
}
