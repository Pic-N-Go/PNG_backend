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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.project.picngo.common.exception.code.NotificationErrorCode;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushConsumer {

    private final FcmService fcmService;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void consumePushEvent(NotificationPushDto dto) {
        try {
            log.info("[RabbitMQ Consumer] Received async push event (userId: {}, type: {})", dto.userId(), dto.type());

            // 1) 멱등성 선점: 알림 이력을 먼저 저장해 중복을 선점한다.
            //    같은 dedupeKey가 이미 있으면 유니크 제약 위반 → 중복으로 간주하고 스킵(FCM 재발송도 하지 않음).
            //    dedupeKey가 null이면(TEST 등) 유니크 제약 대상이 아니라 항상 저장된다.
            Notification notification = Notification.builder()
                    .userId(dto.userId())
                    .type(dto.type())
                    .title(dto.title())
                    .content(dto.content())
                    .deepLink(dto.deepLink())
                    .spotId(dto.spotId())
                    .dedupeKey(dto.dedupeKey())
                    .build();
            try {
                notificationRepository.saveAndFlush(notification);
            } catch (DataIntegrityViolationException e) {
                log.info("♻️ [중복 알림 스킵] 이미 처리된 알림입니다 (dedupeKey: {}, userId: {})", dto.dedupeKey(), dto.userId());
                return; // 이미 처리됨 → 푸시 재발송 방지
            }

            // 2) 최초 처리인 경우에만 FCM 발송
            notificationSettingRepository.findByUserId(dto.userId()).ifPresent(setting -> {
                if (setting.getFcmToken() != null && !setting.getFcmToken().trim().isEmpty()) {
                    try {
                        fcmService.sendMessage(setting.getFcmToken(), dto.title(), dto.content(), dto.deepLink(), dto.spotId());
                    } catch (Exception e) {
                        log.warn("❌ [FCM 발송 실패 (ErrorCode: {})] userId: {}", NotificationErrorCode.FCM_SEND_FAILED.name(), dto.userId(), e);
                    }
                }
            });
        } catch (Throwable t) {
            log.error("❌ [RabbitMQ Consumer 최종 실패] (ErrorCode: {}, userId: {}, 에러: {})",
                    NotificationErrorCode.ASYNC_NOTIFICATION_FAILED.name(), dto.userId(), NotificationErrorCode.ASYNC_NOTIFICATION_FAILED.getMessage(), t);
        }
    }
}
