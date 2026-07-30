package com.project.picngo.notification.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.project.picngo.notification.domain.NotificationSetting;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

@Schema(description = "알림 수신 설정 응답 DTO")
public record NotificationSettingResponse(
        @Schema(description = "위시리스트 알림 수신 여부", example = "true")
        Boolean isWishlistPushEnabled,

        @Schema(description = "골든아워 알림 수신 여부", example = "true")
        Boolean isGoldenHourPushEnabled,

        @Schema(description = "커뮤니티 알림 수신 여부", example = "true")
        Boolean isCommunityPushEnabled,

        @Schema(description = "방해금지 기능 활성화 여부", example = "false")
        Boolean isDndEnabled,

        @Schema(description = "방해금지 시작 시간 (HH:mm)", example = "23:00")
        @JsonFormat(pattern = "HH:mm:ss")
        LocalTime dndStartTime,

        @Schema(description = "방해금지 종료 시간 (HH:mm)", example = "07:00")
        @JsonFormat(pattern = "HH:mm:ss")
        LocalTime dndEndTime
) {
    public static NotificationSettingResponse from(NotificationSetting setting) {
        if (setting == null) {
            return new NotificationSettingResponse(true, true, true, false, null, null);
        }
        return new NotificationSettingResponse(
                setting.getIsWishlistPushEnabled(),
                setting.getIsGoldenHourPushEnabled(),
                setting.getIsCommunityPushEnabled(),
                setting.getIsDndEnabled(),
                setting.getDndStartTime(),
                setting.getDndEndTime()
        );
    }

    public boolean isDndActive() {
        return com.project.picngo.notification.domain.DndPolicy.isActive(
                this.isDndEnabled, this.dndStartTime, this.dndEndTime,
                LocalTime.now(java.time.ZoneId.of("Asia/Seoul")));
    }
}
