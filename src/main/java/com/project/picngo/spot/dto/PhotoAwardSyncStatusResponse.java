package com.project.picngo.spot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "사진공모전 동기화 진행 상태 응답 DTO")
public record PhotoAwardSyncStatusResponse(
        @Schema(description = "현재 동기화 작업 실행 중 여부", example = "true")
        boolean isRunning,

        @Schema(description = "현재 진행 중인 작업 명칭", example = "지역(부산광역시) 동기화")
        String currentJob,

        @Schema(description = "대상 법정동 시도 코드 (지역 동기화 시)", example = "26")
        Integer lDongRegnCd,

        @Schema(description = "총 수집 대상 건수", example = "95")
        int totalCount,

        @Schema(description = "현재 처리 건수 (신규 + 보강)", example = "42")
        int processedCount,

        @Schema(description = "신규 스팟 생성 건수", example = "15")
        int createdCount,

        @Schema(description = "기존 스팟 보강 건수", example = "27")
        int enrichedCount,

        @Schema(description = "진행률 (%)", example = "44.2")
        double progressPercent,

        @Schema(description = "현재 상태 메시지", example = "부산광역시 지역 42/95건 처리 중 (신규 15건, 보강 27건)...")
        String statusMessage,

        @Schema(description = "작업 시작 시각")
        LocalDateTime startedAt,

        @Schema(description = "최근 작업 완료 시각")
        LocalDateTime lastCompletedAt,

        @Schema(description = "최근 발생한 에러 메시지 (실패 시)", example = "null")
        String lastError
) {
    public static PhotoAwardSyncStatusResponse idle(LocalDateTime lastCompletedAt, String lastError) {
        return new PhotoAwardSyncStatusResponse(
                false,
                "IDLE",
                null,
                0,
                0,
                0,
                0,
                0.0,
                "대기 중 (진행 중인 사진공모전 동기화 작업 없음)",
                null,
                lastCompletedAt,
                lastError
        );
    }
}
