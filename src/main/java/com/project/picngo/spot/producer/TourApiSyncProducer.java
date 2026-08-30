package com.project.picngo.spot.producer;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.config.TourApiRabbitMQConfig;
import com.project.picngo.spot.dto.TourApiSyncMessage;
import com.project.picngo.spot.service.TourApiSyncStatusManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiSyncProducer {

    private final RabbitTemplate rabbitTemplate;
    private final TourApiSyncStatusManager syncStatusManager;

    public void sendAreaSync(int areaCode, Integer startPage, Integer endPage, Long adminId) {
        String jobName = "지역(areaCode: " + areaCode + ") 동기화";
        if (!syncStatusManager.tryLock(jobName, areaCode)) {
            throw new CustomException(SpotErrorCode.SYNC_ALREADY_IN_PROGRESS);
        }

        TourApiSyncMessage message = TourApiSyncMessage.ofArea(areaCode, startPage, endPage, adminId);
        try {
            rabbitTemplate.convertAndSend(TourApiRabbitMQConfig.EXCHANGE_NAME, TourApiRabbitMQConfig.ROUTING_KEY, message);
            log.info("[TourApiSyncProducer] 지역 동기화 메시지 큐 발행 완료: areaCode={}, adminId={}", areaCode, adminId);
        } catch (Exception e) {
            syncStatusManager.releaseLock();
            log.error("[TourApiSyncProducer] 큐 메시지 발행 실패: {}", e.getMessage());
            throw e;
        }
    }

    public void sendAllSync(Long adminId) {
        String jobName = "전국 17개 지역 전체 동기화";
        if (!syncStatusManager.tryLock(jobName, null)) {
            throw new CustomException(SpotErrorCode.SYNC_ALREADY_IN_PROGRESS);
        }

        TourApiSyncMessage message = TourApiSyncMessage.ofAll(adminId);
        try {
            rabbitTemplate.convertAndSend(TourApiRabbitMQConfig.EXCHANGE_NAME, TourApiRabbitMQConfig.ROUTING_KEY, message);
            log.info("[TourApiSyncProducer] 전국 전체 동기화 메시지 큐 발행 완료: adminId={}", adminId);
        } catch (Exception e) {
            syncStatusManager.releaseLock();
            log.error("[TourApiSyncProducer] 큐 메시지 발행 실패: {}", e.getMessage());
            throw e;
        }
    }

    public void sendSampleSync(int countPerType, Long adminId) {
        String jobName = "타입별 샘플(" + countPerType + "건) 동기화";
        if (!syncStatusManager.tryLock(jobName, null)) {
            throw new CustomException(SpotErrorCode.SYNC_ALREADY_IN_PROGRESS);
        }

        TourApiSyncMessage message = TourApiSyncMessage.ofSample(countPerType, adminId);
        try {
            rabbitTemplate.convertAndSend(TourApiRabbitMQConfig.EXCHANGE_NAME, TourApiRabbitMQConfig.ROUTING_KEY, message);
            log.info("[TourApiSyncProducer] 샘플 동기화 메시지 큐 발행 완료: countPerType={}, adminId={}", countPerType, adminId);
        } catch (Exception e) {
            syncStatusManager.releaseLock();
            log.error("[TourApiSyncProducer] 큐 메시지 발행 실패: {}", e.getMessage());
            throw e;
        }
    }
}
