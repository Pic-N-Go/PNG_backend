package com.project.picngo.spot.consumer;

import com.project.picngo.spot.config.AccessibilityTourRabbitMQConfig;
import com.project.picngo.spot.dto.AccessibilityTourSyncMessage;
import com.project.picngo.spot.dto.AccessibilityTourSyncResultResponse;
import com.project.picngo.spot.service.AccessibilityTourSyncService;
import com.project.picngo.spot.service.TourApiSyncStatusManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccessibilityTourSyncConsumer {

    private final AccessibilityTourSyncService service;
    private final TourApiSyncStatusManager syncStatusManager;

    @RabbitListener(queues = AccessibilityTourRabbitMQConfig.QUEUE)
    public void consume(AccessibilityTourSyncMessage message) {
        try {
            syncStatusManager.markStageRunning(message.jobId(), TourApiSyncStatusManager.Stage.ACCESSIBILITY,
                    "무장애 정보 동기화 진행 중");
            int totalFailedCount = 0;
            int completedTypes = 0;
            for (int type : message.contentTypeIds()) {
                AccessibilityTourSyncResultResponse result = service.syncMatchedSpots(
                        type,
                        message.maxDetailsPerType(),
                        message.areaCode()
                );
                log.info("[AccessibilityTourSyncConsumer] 타입 완료: type={}, matched={}, requested={}, saved={}, failed={}",
                        type,
                        result.matchedSpotCount(),
                        result.requestedDetailCount(),
                        result.savedCount(),
                        result.failedCount());
                totalFailedCount += result.failedCount();
                completedTypes++;
                syncStatusManager.updateStageProgress(
                        message.jobId(), TourApiSyncStatusManager.Stage.ACCESSIBILITY,
                        completedTypes, message.contentTypeIds().size(),
                        "무장애 정보 타입 " + completedTypes + "/" + message.contentTypeIds().size() + " 처리 완료"
                );
            }

            if (totalFailedCount > 0) {
                throw new IllegalStateException(
                        "무장애 상세 동기화 실패: " + totalFailedCount + "건"
                );
            }
        } catch (RuntimeException e) {
            syncStatusManager.markStageRetrying(
                    message.jobId(), TourApiSyncStatusManager.Stage.ACCESSIBILITY, e.getMessage());
            log.error("[AccessibilityTourSyncConsumer] 실패, RabbitMQ 재시도 대상: {}", e.getMessage(), e);
            throw e;
        }

        syncStatusManager.markStageCompleted(
                message.jobId(), TourApiSyncStatusManager.Stage.ACCESSIBILITY,
                message.contentTypeIds().size(), "무장애 정보 동기화 완료");
    }
}
