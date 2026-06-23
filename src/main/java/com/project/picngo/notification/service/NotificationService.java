package com.project.picngo.notification.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.NotificationErrorCode;
import com.project.picngo.notification.domain.Notification;
import com.project.picngo.notification.domain.NotificationSetting;
import com.project.picngo.notification.dto.NotificationResponse;
import com.project.picngo.notification.dto.NotificationSettingUpdateRequest;
import com.project.picngo.notification.repository.NotificationRepository;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;

    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findAllByUserId(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void updateFcmToken(Long userId, String token) {
        NotificationSetting setting = notificationSettingRepository.findByUserId(userId)
                .orElseGet(() -> notificationSettingRepository.save(NotificationSetting.builder().userId(userId).build()));
        setting.updateFcmToken(token);
    }

    @Transactional
    public void markAsRead(Long id, Long userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new CustomException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUserId().equals(userId)) {
            throw new CustomException(NotificationErrorCode.UNAUTHORIZED_NOTIFICATION_ACCESS);
        }

        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public void updateSettings(Long userId, NotificationSettingUpdateRequest request) {
        NotificationSetting setting = notificationSettingRepository.findByUserId(userId)
                .orElseGet(() -> notificationSettingRepository.save(NotificationSetting.builder().userId(userId).build()));
        
        setting.updateSettings(request.isAllPushEnabled(), request.dndStartTime(), request.dndEndTime());
    }


    @Transactional
    public void sendPushNotification(Long userId, String type, String title, String content, String deepLink) {
        // TODO: Call FCM API (using WebClient) and save Notification entity to DB
    }
}
