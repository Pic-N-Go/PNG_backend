package com.project.picngo.notification.repository;

import com.project.picngo.notification.domain.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {
    Optional<NotificationSetting> findByUserId(Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT n FROM NotificationSetting n WHERE n.isAllPushEnabled = true AND n.fcmToken IS NOT NULL AND n.fcmToken != ''")
    java.util.List<NotificationSetting> findActiveSettingsWithToken();
}
