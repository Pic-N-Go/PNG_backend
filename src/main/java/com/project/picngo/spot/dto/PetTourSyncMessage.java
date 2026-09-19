package com.project.picngo.spot.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public record PetTourSyncMessage(
        List<Integer> contentTypeIds,
        int maxDetailsPerType,
        Integer legalRegionCode,
        Long adminId,
        TourApiSyncMessage.SyncType sourceSyncType,
        LocalDateTime requestedAt
) implements Serializable {

    private static final List<Integer> DEFAULT_CONTENT_TYPE_IDS = List.of(12, 14, 15, 39);
    private static final int DEFAULT_MAX_DETAILS_PER_TYPE = 1_000;

    public static PetTourSyncMessage afterSpotSync(TourApiSyncMessage sourceMessage) {
        return new PetTourSyncMessage(
                DEFAULT_CONTENT_TYPE_IDS,
                maxDetailsPerType(sourceMessage),
                com.project.picngo.spot.service.TourAreaCodeMapper.toLegalRegionCode(sourceMessage.areaCode()),
                sourceMessage.adminId(),
                sourceMessage.syncType(),
                LocalDateTime.now()
        );
    }

    private static int maxDetailsPerType(TourApiSyncMessage sourceMessage) {
        if (sourceMessage.syncType() == TourApiSyncMessage.SyncType.SAMPLE
                && sourceMessage.countPerType() != null) {
            return sourceMessage.countPerType();
        }
        return DEFAULT_MAX_DETAILS_PER_TYPE;
    }
}
