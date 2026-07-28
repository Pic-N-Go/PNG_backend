package com.project.picngo.notification.consumer;

import com.project.picngo.notification.service.FcmService;
import com.project.picngo.notification.config.RabbitMQConfig;
import com.project.picngo.notification.domain.Notification;
import com.project.picngo.notification.dto.NotificationPushDto;
import com.project.picngo.notification.repository.NotificationRepository;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.project.picngo.common.exception.code.NotificationErrorCode;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushConsumer {

    private final FcmService fcmService;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    @Transactional
    public void consumePushEvent(NotificationPushDto dto) {
        try {
            log.info("[RabbitMQ Consumer] Received async push event (userId: {}, type: {})", dto.userId(), dto.type());

            notificationSettingRepository.findByUserId(dto.userId()).ifPresent(setting -> {
                if (setting.getFcmToken() != null && !setting.getFcmToken().trim().isEmpty()) {
                    try {
                        fcmService.sendMessage(setting.getFcmToken(), dto.title(), dto.content(), dto.deepLink(), dto.spotId());
                    } catch (Exception e) {
                        log.warn("❌ [FCM 발송 실패 (ErrorCode: {})] userId: {}", NotificationErrorCode.FCM_SEND_FAILED.name(), dto.userId(), e);
                    }
                }
            });

            Notification notification = Notification.builder()
                    .userId(dto.userId())
                    .type(dto.type())
                    .title(dto.title())
                    .content(dto.content())
                    .deepLink(dto.deepLink())
                    .spotId(dto.spotId())
                    .build();
            notificationRepository.save(notification);
        } catch (Throwable t) {
            log.error("❌ [RabbitMQ Consumer 1회 재시도 후 최종 실패] (ErrorCode: {}, userId: {}, 에러: {})",
                    NotificationErrorCode.ASYNC_NOTIFICATION_FAILED.name(), dto.userId(), NotificationErrorCode.ASYNC_NOTIFICATION_FAILED.getMessage(), t);
        }
    }
}
