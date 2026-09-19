package com.project.picngo.spot.producer;

import com.project.picngo.spot.config.AccessibilityTourRabbitMQConfig;
import com.project.picngo.spot.dto.AccessibilityTourSyncMessage;
import com.project.picngo.spot.dto.TourApiSyncMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccessibilityTourSyncProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendAfterSpotSync(TourApiSyncMessage source) {
        AccessibilityTourSyncMessage message = AccessibilityTourSyncMessage.afterSpotSync(source);
        rabbitTemplate.convertAndSend(
                AccessibilityTourRabbitMQConfig.EXCHANGE,
                AccessibilityTourRabbitMQConfig.KEY,
                message
        );
        log.info("[AccessibilityTourSyncProducer] 무장애 동기화 메시지 발행 완료: source={}",
                source.syncType());
    }
}
