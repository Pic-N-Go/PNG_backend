package com.project.picngo.notification.repository;

import com.project.picngo.notification.domain.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    // 특정 유저 1명의 설정을 찾아볼 때
    Optional<NotificationSetting> findByUserId(Long userId);

    // 위시리스트 알림이 켜져있고 FCM 토큰이 존재하는 유저 목록 조회
    // TODO 캐싱 작업 필수
    @Query("SELECT n FROM NotificationSetting n WHERE n.isWishlistPushEnabled = true AND n.fcmToken IS NOT NULL AND n.fcmToken != ''")
    List<NotificationSetting> findActiveWishlistSettingsWithToken();

    // 골든아워 알림이 켜져있고 FCM 토큰이 존재하는 유저 목록 조회
    // TODO 캐싱 작업 필수
    @Query("SELECT n FROM NotificationSetting n WHERE n.isGoldenHourPushEnabled = true AND n.fcmToken IS NOT NULL AND n.fcmToken != ''")
    List<NotificationSetting> findActiveGoldenHourSettingsWithToken();

    // 커뮤니티 알림이 켜져있고 FCM 토큰이 존재하는 유저 목록 조회
    // TODO 캐싱 작업 필수
    @Query("SELECT n FROM NotificationSetting n WHERE n.isCommunityPushEnabled = true AND n.fcmToken IS NOT NULL AND n.fcmToken != ''")
    List<NotificationSetting> findActiveCommunitySettingsWithToken();
}
