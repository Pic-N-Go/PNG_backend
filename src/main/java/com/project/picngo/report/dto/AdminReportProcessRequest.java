package com.project.picngo.report.dto;

import com.project.picngo.report.domain.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminReportProcessRequest(
        @NotNull(message = "처리 상태는 필수입니다.")
        ReportStatus status,

        @Size(max = 1000, message = "처리 메모는 1000자 이하여야 합니다.")
        String resolutionNote
) {
}
