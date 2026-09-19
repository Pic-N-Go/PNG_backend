package com.project.picngo.spot.consumer;

import com.project.picngo.spot.config.PetTourRabbitMQConfig;
import com.project.picngo.spot.dto.PetTourSyncMessage;
import com.project.picngo.spot.dto.PetTourSyncResultResponse;
import com.project.picngo.spot.service.PetTourSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PetTourSyncConsumer {

    private final PetTourSyncService petTourSyncService;

    @RabbitListener(queues = PetTourRabbitMQConfig.QUEUE_NAME)
    public void consume(PetTourSyncMessage message) {
        log.info("[PetTourSyncConsumer] 펫 동기화 시작: source={}, contentTypes={}",
                message.sourceSyncType(), message.contentTypeIds());

        try {
            int totalFailedCount = 0;
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
            }

            if (totalFailedCount > 0) {
                throw new IllegalStateException(
                        "반려동물 상세 동기화 실패: " + totalFailedCount + "건"
                );
            }
        } catch (RuntimeException e) {
            // 예외를 다시 던져 Spring AMQP 재시도와 DLQ 처리가 동작하게 한다.
            log.error("[PetTourSyncConsumer] 펫 동기화 실패, RabbitMQ 재시도 대상: source={}, cause={}",
                    message.sourceSyncType(), e.getMessage(), e);
            throw e;
        }

        log.info("[PetTourSyncConsumer] 펫 동기화 전체 완료: source={}", message.sourceSyncType());
    }
}
