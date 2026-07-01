package com.project.picngo.notification.repository;

import com.project.picngo.notification.domain.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {
    Optional<NotificationSetting> findByUserId(Long userId);

    @Query("SELECT n FROM NotificationSetting n WHERE n.isAllPushEnabled = true AND n.fcmToken IS NOT NULL AND n.fcmToken != ''")
    List<NotificationSetting> findActiveSettingsWithToken();
}
