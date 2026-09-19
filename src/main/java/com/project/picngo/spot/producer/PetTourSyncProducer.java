package com.project.picngo.spot.producer;

import com.project.picngo.spot.config.PetTourRabbitMQConfig;
import com.project.picngo.spot.dto.PetTourSyncMessage;
import com.project.picngo.spot.dto.TourApiSyncMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PetTourSyncProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendAfterSpotSync(TourApiSyncMessage sourceMessage) {
        PetTourSyncMessage message = PetTourSyncMessage.afterSpotSync(sourceMessage);
        rabbitTemplate.convertAndSend(
                PetTourRabbitMQConfig.EXCHANGE_NAME,
                PetTourRabbitMQConfig.ROUTING_KEY,
                message
        );
        log.info("[PetTourSyncProducer] 펫 동기화 메시지 발행 완료: source={}, contentTypes={}",
                message.sourceSyncType(), message.contentTypeIds());
    }
}
