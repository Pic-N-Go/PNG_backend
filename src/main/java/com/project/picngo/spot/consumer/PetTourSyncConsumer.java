package com.project.picngo.spot.consumer;

import com.project.picngo.spot.config.PetTourRabbitMQConfig;
import com.project.picngo.spot.dto.PetTourSyncMessage;
import com.project.picngo.spot.dto.PetTourSyncResultResponse;
import com.project.picngo.spot.service.PetTourSyncService;
import com.project.picngo.spot.service.TourApiSyncStatusManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PetTourSyncConsumer {

    private final PetTourSyncService petTourSyncService;
    private final TourApiSyncStatusManager syncStatusManager;

    @RabbitListener(queues = PetTourRabbitMQConfig.QUEUE_NAME)
    public void consume(PetTourSyncMessage message) {
        log.info("[PetTourSyncConsumer] 펫 동기화 시작: source={}, contentTypes={}",
                message.sourceSyncType(), message.contentTypeIds());

        try {
            syncStatusManager.markStageRunning(message.jobId(), TourApiSyncStatusManager.Stage.PET,
                    "반려동물 정보 동기화 진행 중");
            int totalFailedCount = 0;
            int completedTypes = 0;
            for (int contentTypeId : message.contentTypeIds()) {
                PetTourSyncResultResponse result = petTourSyncService.syncMatchedSpots(
                        contentTypeId,
                        message.maxDetailsPerType(),
                        message.legalRegionCode()
                );
                log.info("[PetTourSyncConsumer] 타입 동기화 완료: contentTypeId={}, matched={}, requested={}, saved={}, noDetail={}, failed={}",
                        result.contentTypeId(), result.matchedSpotCount(), result.requestedDetailCount(),
                        result.savedCount(), result.noDetailCount(), result.failedCount());
                totalFailedCount += result.failedCount();
                completedTypes++;
                syncStatusManager.updateStageProgress(
                        message.jobId(), TourApiSyncStatusManager.Stage.PET,
                        completedTypes, message.contentTypeIds().size(),
                        "반려동물 정보 타입 " + completedTypes + "/" + message.contentTypeIds().size() + " 처리 완료"
                );
            }

            if (totalFailedCount > 0) {
                throw new IllegalStateException(
                        "반려동물 상세 동기화 실패: " + totalFailedCount + "건"
                );
            }
        } catch (RuntimeException e) {
            syncStatusManager.markStageRetrying(message.jobId(), TourApiSyncStatusManager.Stage.PET, e.getMessage());
            // 예외를 다시 던져 Spring AMQP 재시도와 DLQ 처리가 동작하게 한다.
            log.error("[PetTourSyncConsumer] 펫 동기화 실패, RabbitMQ 재시도 대상: source={}, cause={}",
                    message.sourceSyncType(), e.getMessage(), e);
            throw e;
        }

        syncStatusManager.markStageCompleted(message.jobId(), TourApiSyncStatusManager.Stage.PET,
                message.contentTypeIds().size(), "반려동물 정보 동기화 완료");

        log.info("[PetTourSyncConsumer] 펫 동기화 전체 완료: source={}", message.sourceSyncType());
    }
}
