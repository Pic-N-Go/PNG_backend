package com.project.picngo.report.dto;

import com.project.picngo.report.domain.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(
        @NotNull(message = "신고 사유는 필수입니다.")
        ReportReason reason,

        @Size(max = 500, message = "신고 상세 내용은 500자 이하여야 합니다.")
        String detail
) {
}
