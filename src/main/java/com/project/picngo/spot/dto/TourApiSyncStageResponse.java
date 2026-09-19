package com.project.picngo.spot.dto;

public record TourApiSyncStageResponse(
        TourApiSyncStageStatus status,
        int processedCount,
        int totalCount,
        double progressPercent,
        String message,
        String lastError
) {
}
