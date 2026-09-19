package com.project.picngo.spot.producer;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.external.LegalDongMapper;
import com.project.picngo.spot.config.PhotoAwardRabbitMQConfig;
import com.project.picngo.spot.dto.PhotoAwardSyncMessage;
import com.project.picngo.spot.service.PhotoAwardSyncStatusManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoAwardSyncProducer {

    private final RabbitTemplate rabbitTemplate;
    private final PhotoAwardSyncStatusManager syncStatusManager;

    public void sendAreaSync(int lDongRegnCd, Long adminId) {
        String regionName = LegalDongMapper.getRegionName(lDongRegnCd);
        String jobName = "지역(" + regionName + ") 공모전 동기화";
        if (!syncStatusManager.tryLock(jobName, lDongRegnCd)) {
            throw new CustomException(SpotErrorCode.SYNC_ALREADY_IN_PROGRESS);
        }

        PhotoAwardSyncMessage message = PhotoAwardSyncMessage.ofArea(lDongRegnCd, adminId);
        try {
            rabbitTemplate.convertAndSend(PhotoAwardRabbitMQConfig.EXCHANGE_NAME, PhotoAwardRabbitMQConfig.ROUTING_KEY, message);
            log.info("[PhotoAwardSyncProducer] 지역 공모전 동기화 큐 메시지 발행 완료: lDongRegnCd={}, adminId={}", lDongRegnCd, adminId);
        } catch (Exception e) {
            syncStatusManager.releaseLock();
            log.error("[PhotoAwardSyncProducer] 큐 메시지 발행 실패: {}", e.getMessage());
            throw e;
        }
    }

    public void sendAllSync(Long adminId) {
        String jobName = "전국 17개 지역 사진공모전 전체 동기화";
        if (!syncStatusManager.tryLock(jobName, null)) {
            throw new CustomException(SpotErrorCode.SYNC_ALREADY_IN_PROGRESS);
        }

        PhotoAwardSyncMessage message = PhotoAwardSyncMessage.ofAll(adminId);
        try {
            rabbitTemplate.convertAndSend(PhotoAwardRabbitMQConfig.EXCHANGE_NAME, PhotoAwardRabbitMQConfig.ROUTING_KEY, message);
            log.info("[PhotoAwardSyncProducer] 전국 공모전 전체 동기화 큐 메시지 발행 완료: adminId={}", adminId);
        } catch (Exception e) {
            syncStatusManager.releaseLock();
            log.error("[PhotoAwardSyncProducer] 큐 메시지 발행 실패: {}", e.getMessage());
            throw e;
        }
    }
}
