package com.project.picngo.notification.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.NotificationErrorCode;
import com.project.picngo.notification.domain.Notification;
import com.project.picngo.notification.domain.NotificationSetting;
import com.project.picngo.notification.dto.NotificationResponse;
import com.project.picngo.notification.dto.NotificationSettingUpdateRequest;
import com.project.picngo.notification.dto.NotificationTestRequest;
import com.project.picngo.notification.repository.NotificationRepository;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final FcmService fcmService;

    @Transactional(readOnly = true)
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


    public void sendPushNotification(Long userId, String type, String title, String content, String deepLink) {
        notificationSettingRepository.findByUserId(userId).ifPresent(setting -> {
            if (Boolean.TRUE.equals(setting.getIsAllPushEnabled()) && setting.getFcmToken() != null && !setting.getFcmToken().isEmpty()) {
                try {
                    fcmService.sendMessage(setting.getFcmToken(), title, content, deepLink);
                } catch (Exception e) {
                    log.warn("Failed to send FCM push to userId: {}", userId, e);
                }
            }
        });

        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .deepLink(deepLink)
                .build();
        notificationRepository.save(notification);
    }

    public void sendTestPushNotification(Long userId, NotificationTestRequest request) {
        String title = (request != null && request.title() != null && !request.title().isBlank())
                ? request.title() : "픽앤고 테스트 알림 🔔";
        String content = (request != null && request.content() != null && !request.content().isBlank())
                ? request.content() : "프론트엔드 푸시 알림 수신 성공 테스트 메시지입니다!";
        String deepLink = (request != null && request.deepLink() != null && !request.deepLink().isBlank())
                ? request.deepLink() : "/wishlist/1";

        sendPushNotification(userId, "TEST", title, content, deepLink);
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void deleteOldNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        int deletedCount;
        int totalDeleted = 0;
        
        do {
            deletedCount = notificationRepository.deleteByCreatedAtBeforeWithLimit(cutoff, 1000);
            totalDeleted += deletedCount;
        } while (deletedCount == 1000);
        
        log.info("Old notifications before {} have been deleted. Total deleted: {}", cutoff, totalDeleted);
    }
}
