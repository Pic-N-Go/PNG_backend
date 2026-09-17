package com.project.picngo.report.dto;

import com.project.picngo.report.domain.Report;
import com.project.picngo.report.domain.ReportStatus;

import java.time.LocalDateTime;

public record ReportCreateResponse(
        Long reportId,
        ReportStatus status,
        LocalDateTime createdAt
) {

    public static ReportCreateResponse from(Report report) {
        return new ReportCreateResponse(
                report.getId(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
