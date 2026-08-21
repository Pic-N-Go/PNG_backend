package com.project.picngo.contest.dto;

import com.project.picngo.contest.domain.ContestReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContestReportRequest(

        @NotNull(message = "신고 사유는 필수입니다.")
        ContestReportReason reason,

        @Size(max = 500, message = "신고 내용은 최대 500자까지 입력할 수 있습니다.")
        String content
) {
}
