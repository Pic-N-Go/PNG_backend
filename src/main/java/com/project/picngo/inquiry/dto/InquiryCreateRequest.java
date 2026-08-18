package com.project.picngo.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "1:1 문의 작성 요청 DTO")
public record InquiryCreateRequest(
        @Schema(description = "문의 제목", example = "사진 코스 등록 관련 질문드립니다.")
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 150, message = "제목은 최대 150자까지 입력 가능합니다.")
        String title,

        @Schema(description = "문의 내용", example = "제주도 코스 생성 시 특정 스팟이 포함되지 않는데 확인 부탁드립니다.")
        @NotBlank(message = "내용은 필수입니다.")
        String content
) {
}
