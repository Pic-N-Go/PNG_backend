package com.project.picngo.notification.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.ZoneId;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    private String fcmToken;

    @Column(nullable = false)
    private Boolean isSpotAlertPushEnabled = true;

    @Column(nullable = false)
    private Boolean isGoldenHourPushEnabled = true;

    @Column(nullable = false)
    private Boolean isCommunityPushEnabled = true;

    @Column(nullable = false)
    private Boolean isDndEnabled = false;

    private LocalTime dndStartTime;

    private LocalTime dndEndTime;

    @Builder
    public NotificationSetting(Long userId, String fcmToken, Boolean isSpotAlertPushEnabled, Boolean isGoldenHourPushEnabled, Boolean isCommunityPushEnabled, Boolean isDndEnabled) {
        this.userId = userId;
        this.fcmToken = fcmToken;
        this.isSpotAlertPushEnabled = isSpotAlertPushEnabled != null ? isSpotAlertPushEnabled : true;
        this.isGoldenHourPushEnabled = isGoldenHourPushEnabled != null ? isGoldenHourPushEnabled : true;
        this.isCommunityPushEnabled = isCommunityPushEnabled != null ? isCommunityPushEnabled : true;
        this.isDndEnabled = isDndEnabled != null ? isDndEnabled : false;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public void updateSettings(Boolean isSpotAlertPushEnabled, Boolean isGoldenHourPushEnabled, Boolean isCommunityPushEnabled, Boolean isDndEnabled, LocalTime dndStartTime, LocalTime dndEndTime) {
        if (isSpotAlertPushEnabled != null) {
            this.isSpotAlertPushEnabled = isSpotAlertPushEnabled;
        }
        if (isGoldenHourPushEnabled != null) {
            this.isGoldenHourPushEnabled = isGoldenHourPushEnabled;
        }
        if (isCommunityPushEnabled != null) {
            this.isCommunityPushEnabled = isCommunityPushEnabled;
        }
        if (isDndEnabled != null) {
            this.isDndEnabled = isDndEnabled;
        }
        this.dndStartTime = dndStartTime;
        this.dndEndTime = dndEndTime;
    }

    public boolean isDndActive() {
        return DndPolicy.isActive(this.isDndEnabled, this.dndStartTime, this.dndEndTime,
                LocalTime.now(ZoneId.of("Asia/Seoul")));
    }
}



