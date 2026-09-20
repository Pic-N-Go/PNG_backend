package com.project.picngo.spot.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public record AccessibilityTourSyncMessage(
        String jobId,
        List<Integer> contentTypeIds,
        int maxDetailsPerType,
        Integer areaCode,
        Long adminId,
        TourApiSyncMessage.SyncType sourceSyncType,
        LocalDateTime requestedAt
) implements Serializable {

    private static final List<Integer> DEFAULT_CONTENT_TYPE_IDS = List.of(12, 14, 15, 39);
    private static final int DEFAULT_MAX_DETAILS_PER_TYPE = 1_000;

    public static AccessibilityTourSyncMessage afterSpotSync(TourApiSyncMessage source) {
        return new AccessibilityTourSyncMessage(
                source.jobId(),
                DEFAULT_CONTENT_TYPE_IDS,
                maxDetailsPerType(source),
                source.areaCode(),
                source.adminId(),
                source.syncType(),
                LocalDateTime.now()
        );
    }

    private static int maxDetailsPerType(TourApiSyncMessage source) {
        if (source.syncType() == TourApiSyncMessage.SyncType.SAMPLE
                && source.countPerType() != null) {
            return source.countPerType();
        }
        return DEFAULT_MAX_DETAILS_PER_TYPE;
    }
}
