package com.project.picngo.spot.consumer;

import com.project.picngo.spot.config.AccessibilityTourRabbitMQConfig;
import com.project.picngo.spot.dto.AccessibilityTourSyncMessage;
import com.project.picngo.spot.dto.AccessibilityTourSyncResultResponse;
import com.project.picngo.spot.service.AccessibilityTourSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccessibilityTourSyncConsumer {

    private final AccessibilityTourSyncService service;

    @RabbitListener(queues = AccessibilityTourRabbitMQConfig.QUEUE)
    public void consume(AccessibilityTourSyncMessage message) {
        try {
            int totalFailedCount = 0;
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
            }

            if (totalFailedCount > 0) {
                throw new IllegalStateException(
                        "무장애 상세 동기화 실패: " + totalFailedCount + "건"
                );
            }
        } catch (RuntimeException e) {
            log.error("[AccessibilityTourSyncConsumer] 실패, RabbitMQ 재시도 대상: {}", e.getMessage(), e);
            throw e;
        }
    }
}
