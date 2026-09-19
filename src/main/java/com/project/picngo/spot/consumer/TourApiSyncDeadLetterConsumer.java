package com.project.picngo.spot.consumer;

import com.project.picngo.spot.config.AccessibilityTourRabbitMQConfig;
import com.project.picngo.spot.config.PetTourRabbitMQConfig;
import com.project.picngo.spot.dto.AccessibilityTourSyncMessage;
import com.project.picngo.spot.dto.PetTourSyncMessage;
import com.project.picngo.spot.service.TourApiSyncStatusManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiSyncDeadLetterConsumer {

    private final TourApiSyncStatusManager syncStatusManager;

    @RabbitListener(queues = PetTourRabbitMQConfig.DEAD_LETTER_STATUS_QUEUE_NAME)
    public void consumePetFailure(PetTourSyncMessage message) {
        String error = "반려동물 정보 동기화가 재시도 후 최종 실패했습니다.";
        log.error("[TourApiSyncDeadLetterConsumer] 펫 동기화 최종 실패: jobId={}", message.jobId());
        syncStatusManager.markStageFailed(message.jobId(), TourApiSyncStatusManager.Stage.PET, error);
    }

    @RabbitListener(queues = AccessibilityTourRabbitMQConfig.DLQ_STATUS)
    public void consumeAccessibilityFailure(AccessibilityTourSyncMessage message) {
        String error = "무장애 정보 동기화가 재시도 후 최종 실패했습니다.";
        log.error("[TourApiSyncDeadLetterConsumer] 무장애 동기화 최종 실패: jobId={}", message.jobId());
        syncStatusManager.markStageFailed(
                message.jobId(), TourApiSyncStatusManager.Stage.ACCESSIBILITY, error);
    }
}
