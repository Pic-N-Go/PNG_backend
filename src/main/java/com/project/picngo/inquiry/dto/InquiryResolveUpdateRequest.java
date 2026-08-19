package com.project.picngo.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "1:1 문의 해결 상태 변경 요청 DTO")
public record InquiryResolveUpdateRequest(
        @Schema(description = "해결 여부 (true: 해결됨, false: 미해결)", example = "true")
        @NotNull(message = "해결 여부(isResolved)는 필수입니다.")
        Boolean isResolved
) {
}
