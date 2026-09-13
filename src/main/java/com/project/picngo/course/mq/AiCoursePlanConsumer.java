package com.project.picngo.course.mq;

import com.project.picngo.course.agent.AiCoursePlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiCoursePlanConsumer {

    private final AiCoursePlanService aiCoursePlanService;

    @RabbitListener(queues = AiCourseRabbitConfig.QUEUE_NAME)
    public void consume(AiCoursePlanMessage message) {
        log.info("📤 [RabbitMQ Dequeue] AI 코스 기획 메시지 수신: taskId={}, userId={}",
                message.taskId(), message.userId());
        aiCoursePlanService.executePlan(message);
    }
}
