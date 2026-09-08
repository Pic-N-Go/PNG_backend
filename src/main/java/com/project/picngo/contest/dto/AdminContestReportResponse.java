package com.project.picngo.contest.dto;

import com.project.picngo.contest.domain.ContestReport;
import com.project.picngo.contest.domain.ContestReportReason;

import java.time.LocalDateTime;

public record AdminContestReportResponse(
        Long reportId,
        Long entryId,
        String entryPhotoUrl,
        String entryCaption,
        Long entryAuthorId,
        String entryAuthorNickname,
        Long reporterId,
        String reporterNickname,
        ContestReportReason reason,
        String content,
        LocalDateTime createdAt
) {
    public static AdminContestReportResponse from(ContestReport report) {
        return new AdminContestReportResponse(
                report.getId(),
                report.getEntry().getId(),
                report.getEntry().getPhotoUrl(),
                report.getEntry().getCaption(),
                report.getEntry().getUser().getId(),
                report.getEntry().getUser().getNickname(),
                report.getUser().getId(),
                report.getUser().getNickname(),
                report.getReason(),
                report.getContent(),
                report.getCreatedAt()
        );
    }
}
