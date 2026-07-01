package com.project.picngo.notification.repository;

import com.project.picngo.notification.domain.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    // 특정 유저 1명의 설정을 찾아볼 때
    Optional<NotificationSetting> findByUserId(Long userId);

    // 모든 유저 중에서 알림을 켜두었고 (isAllPushEnabled가 true) fcmToken이 null이 아니고 빈 문자열이 아닌 유저 목록 조회
    // 전체 공지용 알림 보낼 때 사용
    @Query("SELECT n FROM NotificationSetting n WHERE n.isAllPushEnabled = true AND n.fcmToken IS NOT NULL AND n.fcmToken != ''")
    List<NotificationSetting> findActiveSettingsWithToken();
}
