package com.project.picngo.course.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiCoursePlanProducer {

    private final RabbitTemplate rabbitTemplate;

    public void enqueue(AiCoursePlanMessage message) {
        log.info("📥 [RabbitMQ Enqueue] AI 코스 기획 요청 큐 발행: taskId={}, userId={}", message.taskId(), message.userId());
        rabbitTemplate.convertAndSend(
                AiCourseRabbitConfig.EXCHANGE_NAME,
                AiCourseRabbitConfig.ROUTING_KEY,
                message
        );
    }
}
