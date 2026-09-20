package com.project.picngo.spot.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public record TourApiSyncMessage(
        String jobId,
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
        return ofArea(null, areaCode, startPage, endPage, adminId);
    }

    public static TourApiSyncMessage ofArea(String jobId, int areaCode, Integer startPage, Integer endPage, Long adminId) {
        return new TourApiSyncMessage(jobId, SyncType.AREA, areaCode, startPage, endPage, null, adminId, LocalDateTime.now());
    }

    public static TourApiSyncMessage ofAll(Long adminId) {
        return ofAll(null, adminId);
    }

    public static TourApiSyncMessage ofAll(String jobId, Long adminId) {
        return new TourApiSyncMessage(jobId, SyncType.ALL, null, null, null, null, adminId, LocalDateTime.now());
    }

    public static TourApiSyncMessage ofSample(int countPerType, Long adminId) {
        return ofSample(null, countPerType, adminId);
    }

    public static TourApiSyncMessage ofSample(String jobId, int countPerType, Long adminId) {
        return new TourApiSyncMessage(jobId, SyncType.SAMPLE, null, null, null, countPerType, adminId, LocalDateTime.now());
    }
}
