package com.project.picngo.notification.dto;

import java.time.LocalTime;

public record NotificationSettingUpdateRequest(
        Boolean isSpotAlertPushEnabled,
        Boolean isGoldenHourPushEnabled,
        Boolean isCommunityPushEnabled,
        Boolean isDndEnabled,
        LocalTime dndStartTime,
        LocalTime dndEndTime
) {}
