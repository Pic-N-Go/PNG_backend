package com.project.picngo.notification.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.NotificationErrorCode;
import com.project.picngo.notification.domain.Notification;
import com.project.picngo.notification.domain.NotificationSetting;
import com.project.picngo.notification.dto.NotificationResponse;
import com.project.picngo.notification.dto.NotificationSettingResponse;
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
    private final NotificationCacheService notificationCacheService;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        return notificationRepository.findAllRecentNotificationsByUserId(userId, cutoff).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void updateFcmToken(Long userId, String token) {
        log.info("\n==================================================" +
                "\n[📱 FCM 기기 토큰 등록/갱신 성공]" +
                "\n- UserId: {}" +
                "\n- FCM Token: {}" +
                "\n==================================================",
                userId, token);
        NotificationSetting setting = notificationSettingRepository.findByUserId(userId)
                .orElseGet(() -> notificationSettingRepository.save(NotificationSetting.builder().userId(userId).build()));
        setting.updateFcmToken(token);
        notificationCacheService.updateCachedSetting(userId, setting);
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

    @Transactional(readOnly = true)
    public NotificationSettingResponse getSettings(Long userId) {
        return notificationCacheService.getCachedSetting(userId);
    }

    @Transactional
    public void updateSettings(Long userId, NotificationSettingUpdateRequest request) {
        NotificationSetting setting = notificationSettingRepository.findByUserId(userId)
                .orElseGet(() -> notificationSettingRepository.save(NotificationSetting.builder().userId(userId).build()));
        
        setting.updateSettings(
                request.isWishlistPushEnabled(),
                request.isGoldenHourPushEnabled(),
                request.isCommunityPushEnabled(),
                request.isDndEnabled(),
                request.dndStartTime(),
                request.dndEndTime()
        );

        notificationCacheService.updateCachedSetting(userId, setting);
    }


    public void sendPushNotification(Long userId, String type, String title, String content, String deepLink) {
        sendPushNotification(userId, type, title, content, deepLink, null);
    }

    public void sendPushNotification(Long userId, String type, String title, String content, String deepLink, Long spotId) {
        notificationSettingRepository.findByUserId(userId).ifPresent(setting -> {
            boolean isPushEnabled = isPushEnabledForType(setting, type);
            boolean isDnd = setting.isDndActive();
            if (isPushEnabled && !isDnd && setting.getFcmToken() != null && !setting.getFcmToken().isEmpty()) {
                try {
                    fcmService.sendMessage(setting.getFcmToken(), title, content, deepLink, spotId);
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
                .spotId(spotId)
                .build();
        notificationRepository.save(notification);
    }

    /**
     * 알림 발송 직전, 알림 종류(type)에 따라 유저의 해당 토글 수신 동의 여부(isWishlistPushEnabled 등)를 검사하는 이중 안전장치 메서드
     */
    private boolean isPushEnabledForType(NotificationSetting setting, String type) {
        if (setting == null) return false;
        if ("GOLDEN_HOUR".equalsIgnoreCase(type)) {
            return Boolean.TRUE.equals(setting.getIsGoldenHourPushEnabled());
        } else if ("WEATHER_MATCH".equalsIgnoreCase(type) || "WISHLIST".equalsIgnoreCase(type)) {
            return Boolean.TRUE.equals(setting.getIsWishlistPushEnabled());
        } else if ("COMMUNITY".equalsIgnoreCase(type)) {
            return Boolean.TRUE.equals(setting.getIsCommunityPushEnabled());
        } else if ("TEST".equalsIgnoreCase(type)) {
            // 테스트 알림 발송 시: 위시리스트 + 골든아워 알림이 둘 다 켜져있는지 검사
            return Boolean.TRUE.equals(setting.getIsWishlistPushEnabled()) && Boolean.TRUE.equals(setting.getIsGoldenHourPushEnabled());
        }
        return true;
    }

    // TODO: 운영 배포 전 또는 프론트엔드 알림 테스트 완료 후 테스트용 알림 발송 메서드 삭제 필요
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
