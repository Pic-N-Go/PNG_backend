package com.project.picngo.spot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "TourAPI 동기화 진행 상태 응답 DTO")
public record TourApiSyncStatusResponse(
        @Schema(description = "현재 동기화 작업 실행 중 여부", example = "true")
        boolean isRunning,

        @Schema(description = "현재 진행 중인 작업 종류", example = "AREA (충남)")
        String currentJob,

        @Schema(description = "대상 지역 코드 (지역 동기화 시)", example = "34")
        Integer areaCode,

        @Schema(description = "총 수집 대상 건수", example = "612")
        int totalCount,

        @Schema(description = "현재 저장 완료 건수", example = "150")
        int processedCount,

        @Schema(description = "진행률 (%)", example = "24.5")
        double progressPercent,

        @Schema(description = "현재 상태 메시지", example = "충남 지역 150/612건 저장 중...")
        String statusMessage,

        @Schema(description = "작업 시작 시각")
        LocalDateTime startedAt,

        @Schema(description = "최근 작업 완료 시각")
        LocalDateTime lastCompletedAt,

        @Schema(description = "최근 발생한 에러 메시지 (실패 시)", example = "null")
        String lastError
) {
    public static TourApiSyncStatusResponse idle(LocalDateTime lastCompletedAt, String lastError) {
        return new TourApiSyncStatusResponse(
                false,
                "IDLE",
                null,
                0,
                0,
                0.0,
                "대기 중 (진행 중인 동기화 작업 없음)",
                null,
                lastCompletedAt,
                lastError
        );
    }
}
