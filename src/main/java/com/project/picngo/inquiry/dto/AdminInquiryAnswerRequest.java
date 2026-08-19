package com.project.picngo.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 1:1 문의 답변 작성 요청 DTO")
public record AdminInquiryAnswerRequest(
        @Schema(description = "관리자 답변 내용", example = "안녕하세요 PicNGo입니다. 해당 문의 건은 코스 생성 규칙에 따라...")
        @NotBlank(message = "답변 내용은 필수입니다.")
        String answer
) {
}
