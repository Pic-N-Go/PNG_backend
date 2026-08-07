package com.project.picngo.notification.producer;

import com.project.picngo.notification.config.RabbitMQConfig;
import com.project.picngo.notification.dto.NotificationPushDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPushProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendPushEvent(NotificationPushDto dto) {
        log.info("[RabbitMQ Producer] Push Notification Event sent to Queue (userId: {}, type: {})", dto.userId(), dto.type());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY, dto);
    }
}
