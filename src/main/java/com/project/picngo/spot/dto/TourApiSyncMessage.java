package com.project.picngo.spot.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public record TourApiSyncMessage(
        SyncType syncType,
        Integer areaCode,
        Integer startPage,
        Integer endPage,
        Integer countPerType,
        Long adminId,
        LocalDateTime requestedAt
) implements Serializable {

    public enum SyncType {
        AREA,
        ALL,
        SAMPLE
    }

    public static TourApiSyncMessage ofArea(int areaCode, Integer startPage, Integer endPage, Long adminId) {
        return new TourApiSyncMessage(SyncType.AREA, areaCode, startPage, endPage, null, adminId, LocalDateTime.now());
    }

    public static TourApiSyncMessage ofAll(Long adminId) {
        return new TourApiSyncMessage(SyncType.ALL, null, null, null, null, adminId, LocalDateTime.now());
    }

    public static TourApiSyncMessage ofSample(int countPerType, Long adminId) {
        return new TourApiSyncMessage(SyncType.SAMPLE, null, null, null, countPerType, adminId, LocalDateTime.now());
    }
}
