package com.project.picngo.notification.dto;

import java.time.LocalTime;

public record NotificationSettingUpdateRequest(
        Boolean isWishlistPushEnabled,
        Boolean isGoldenHourPushEnabled,
        Boolean isCommunityPushEnabled,
        LocalTime dndStartTime,
        LocalTime dndEndTime
) {}
