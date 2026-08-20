package com.project.picngo.notification.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.NotificationErrorCode;
import com.project.picngo.notification.domain.Notification;
import com.project.picngo.notification.domain.NotificationSetting;
import com.project.picngo.notification.dto.NotificationResponse;
import com.project.picngo.notification.dto.NotificationSettingResponse;
import com.project.picngo.notification.dto.NotificationSettingUpdateRequest;
import com.project.picngo.notification.repository.NotificationRepository;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import com.project.picngo.notification.dto.NotificationPushDto;
import com.project.picngo.notification.producer.NotificationPushProducer;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final FcmService fcmService;
    private final NotificationCacheService notificationCacheService;
    private final NotificationPushProducer notificationPushProducer;

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

    /**
     * 무효(만료/재발급) FCM 토큰 정리.
     * DB의 fcmToken을 비우고 캐시를 동기화하여, 활성 유저 Set에서도 제외되게 한다.
     * (죽은 토큰으로 매 스케줄러마다 발송을 재시도하는 낭비 방지)
     *
     * <p>비동기 발송 지연 사이에 유저가 새 토큰을 재등록했을 수 있으므로,
     * 저장된 토큰이 <b>실제로 실패한 토큰과 일치할 때만</b> 정리한다(정상 토큰 삭제 방지).
     */
    @Transactional
    public void handleInvalidToken(Long userId, String failedToken) {
        notificationSettingRepository.findByUserId(userId).ifPresent(setting -> {
            if (failedToken != null && failedToken.equals(setting.getFcmToken())) {
                setting.updateFcmToken(null);
                notificationCacheService.updateCachedSetting(userId, setting);
                log.info("♻️ 무효 FCM 토큰 제거 및 활성 대상에서 제외 완료 (userId: {})", userId);
            } else {
                log.info("무효 토큰 정리 스킵 - 저장된 토큰이 실패한 토큰과 달라 이미 갱신된 것으로 간주 (userId: {})", userId);
            }
        });
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
                request.isSpotAlertPushEnabled(),
                request.isGoldenHourPushEnabled(),
                request.isCommunityPushEnabled(),
                request.isDndEnabled(),
                request.dndStartTime(),
                request.dndEndTime()
        );

        notificationCacheService.updateCachedSetting(userId, setting);
    }


    public void sendPushNotification(Long userId, String type, String title, String content, String deepLink) {
        sendPushNotification(userId, type, title, content, deepLink, null, null);
    }

    public void sendPushNotification(Long userId, String type, String title, String content, String deepLink, Long spotId) {
        sendPushNotification(userId, type, title, content, deepLink, spotId, null);
    }

    public void sendPushNotification(Long userId, String type, String title, String content, String deepLink, Long spotId, String dedupeKey) {
        NotificationSettingResponse setting = notificationCacheService.getCachedSetting(userId);
        if (setting != null) {
            boolean isPushEnabled = isPushEnabledForType(setting, type);
            boolean isDnd = setting.isDndActive();
            if (isPushEnabled && !isDnd) {
                // RabbitMQ 비동기 메시지 큐 이벤트 발송 (소요 시간 0.001초 만에 큐로 발송 완료!)
                notificationPushProducer.sendPushEvent(new NotificationPushDto(userId, type, title, content, deepLink, spotId, dedupeKey));
            }
        }
    }

    /**
     * 알림 발송 직전, 알림 종류(type)에 따라 유저의 해당 토글 수신 동의 여부(isSpotAlertPushEnabled 등)를 검사하는 이중 안전장치 메서드
     */
    private boolean isPushEnabledForType(NotificationSettingResponse setting, String type) {
        if (setting == null) return false;
        if ("GOLDEN_HOUR".equalsIgnoreCase(type)) {
            return !Boolean.FALSE.equals(setting.isGoldenHourPushEnabled());
        } else if ("WEATHER_MATCH".equalsIgnoreCase(type) || "SPOT_ALERT".equalsIgnoreCase(type)) {
            return !Boolean.FALSE.equals(setting.isSpotAlertPushEnabled());
        } else if (type != null && type.toUpperCase().startsWith("COMMUNITY")) {
            return !Boolean.FALSE.equals(setting.isCommunityPushEnabled());
        }
        return true;
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
