package com.project.picngo.inquiry.dto;

import com.project.picngo.inquiry.domain.Inquiry;
import com.project.picngo.inquiry.domain.InquiryStatus;
import com.project.picngo.inquiry.domain.InquiryType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "1:1 문의 상세 및 목록 응답 DTO")
public record InquiryResponse(
        @Schema(description = "문의 ID", example = "1")
        Long id,

        @Schema(description = "작성자 회원 ID", example = "10")
        Long userId,

        @Schema(description = "작성자 닉네임", example = "사진가")
        String userNickname,

        @Schema(description = "작성자 이메일", example = "user@example.com")
        String userEmail,

        @Schema(description = "문의 유형 (FEATURE, BUG, ACCOUNT, SPOT, OTHER)", example = "FEATURE")
        InquiryType type,

        @Schema(description = "문의 제목", example = "코스 등록 질문")
        String title,

        @Schema(description = "문의 내용", example = "코스 등록 시 스팟 추가 관련 질문...")
        String content,

        @Schema(description = "관리자 답변 내용", example = "안녕하세요, PicNGo 팀입니다...")
        String answer,

        @Schema(description = "답변 작성 관리자 닉네임", example = "최고관리자")
        String answeredByNickname,

        @Schema(description = "답변 작성 일시")
        LocalDateTime answeredAt,

        @Schema(description = "사용자 해결 여부", example = "true")
        boolean isResolved,

        @Schema(description = "문의 상태 (PENDING, ANSWERED, RESOLVED)", example = "ANSWERED")
        InquiryStatus status,

        @Schema(description = "문의 작성 일시")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시")
        LocalDateTime updatedAt
) {
    public static InquiryResponse from(Inquiry inquiry) {
        return new InquiryResponse(
                inquiry.getId(),
                inquiry.getUser().getId(),
                inquiry.getUser().getNickname(),
                inquiry.getUser().getEmail(),
                inquiry.getType(),
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getAnswer(),
                inquiry.getAnsweredBy() != null ? inquiry.getAnsweredBy().getNickname() : null,
                inquiry.getAnsweredAt(),
                inquiry.isResolved(),
                inquiry.getStatus(),
                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt()
        );
    }
}
