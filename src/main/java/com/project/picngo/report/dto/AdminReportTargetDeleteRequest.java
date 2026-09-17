package com.project.picngo.report.dto;

import jakarta.validation.constraints.Size;

public record AdminReportTargetDeleteRequest(
        @Size(max = 1000, message = "처리 메모는 1000자 이하여야 합니다.")
        String resolutionNote
) {
}
