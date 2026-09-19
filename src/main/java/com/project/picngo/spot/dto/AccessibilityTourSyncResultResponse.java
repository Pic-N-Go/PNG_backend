package com.project.picngo.spot.dto;

public record AccessibilityTourSyncResultResponse(
        int contentTypeId,
        int matchedSpotCount,
        int requestedDetailCount,
        int savedCount,
        int noDetailCount,
        int failedCount
) {
}
