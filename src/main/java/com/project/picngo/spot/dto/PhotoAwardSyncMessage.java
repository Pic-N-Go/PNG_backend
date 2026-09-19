package com.project.picngo.spot.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public record PhotoAwardSyncMessage(
        SyncType syncType,
        Integer lDongRegnCd,
        Long adminId,
        LocalDateTime requestedAt
) implements Serializable {

    public enum SyncType {
        AREA,
        ALL
    }

    public static PhotoAwardSyncMessage ofArea(int lDongRegnCd, Long adminId) {
        return new PhotoAwardSyncMessage(SyncType.AREA, lDongRegnCd, adminId, LocalDateTime.now());
    }

    public static PhotoAwardSyncMessage ofAll(Long adminId) {
        return new PhotoAwardSyncMessage(SyncType.ALL, null, adminId, LocalDateTime.now());
    }
}
