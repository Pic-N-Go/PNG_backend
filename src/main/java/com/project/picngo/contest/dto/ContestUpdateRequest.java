package com.project.picngo.contest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(description = "콘테스트 정보 및 일정 수정 요청")
public record ContestUpdateRequest(

        @Schema(description = "콘테스트 테마/제목", example = "늦가을 단풍 출사전")
        @Size(max = 100, message = "테마는 최대 100자까지 입력할 수 있습니다.")
        String title,

        @Schema(description = "콘테스트 설명", example = "가을의 마지막 정취를 담은 사진을 출품해주세요.")
        @Size(max = 500, message = "설명은 최대 500자까지 입력할 수 있습니다.")
        String description,

        @Schema(description = "테마 대표 이미지 URL", example = "https://example.com/autumn.jpg")
        @Size(max = 500, message = "테마 이미지 URL은 최대 500자까지 입력할 수 있습니다.")
        String themeImageUrl,

        @Schema(description = "새로운 출품 시작 시각 (출품 시작 전인 UPCOMING 상태일 때만 변경 가능하며, 변경 시 4주 일정이 자동 재계산됩니다)", example = "2026-10-15T09:00:00")
        LocalDateTime submitStartAt
) {
}
