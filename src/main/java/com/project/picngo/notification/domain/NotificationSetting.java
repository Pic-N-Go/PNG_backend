package com.project.picngo.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    private String fcmToken;

    @Column(nullable = false)
    private Boolean isWishlistPushEnabled = true;

    @Column(nullable = false)
    private Boolean isGoldenHourPushEnabled = true;

    @Column(nullable = false)
    private Boolean isCommunityPushEnabled = true;

    private LocalTime dndStartTime;

    private LocalTime dndEndTime;

    @Builder
    public NotificationSetting(Long userId, String fcmToken, Boolean isWishlistPushEnabled, Boolean isGoldenHourPushEnabled, Boolean isCommunityPushEnabled) {
        this.userId = userId;
        this.fcmToken = fcmToken;
        this.isWishlistPushEnabled = isWishlistPushEnabled != null ? isWishlistPushEnabled : true;
        this.isGoldenHourPushEnabled = isGoldenHourPushEnabled != null ? isGoldenHourPushEnabled : true;
        this.isCommunityPushEnabled = isCommunityPushEnabled != null ? isCommunityPushEnabled : true;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public void updateSettings(Boolean isWishlistPushEnabled, Boolean isGoldenHourPushEnabled, Boolean isCommunityPushEnabled, LocalTime dndStartTime, LocalTime dndEndTime) {
        if (isWishlistPushEnabled != null) {
            this.isWishlistPushEnabled = isWishlistPushEnabled;
        }
        if (isGoldenHourPushEnabled != null) {
            this.isGoldenHourPushEnabled = isGoldenHourPushEnabled;
        }
        if (isCommunityPushEnabled != null) {
            this.isCommunityPushEnabled = isCommunityPushEnabled;
        }
        this.dndStartTime = dndStartTime;
        this.dndEndTime = dndEndTime;
    }
}



