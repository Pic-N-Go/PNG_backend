package com.project.picngo.course.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiCourseRabbitConfig {

    public static final String QUEUE_NAME = "course.ai.plan.queue";
    public static final String EXCHANGE_NAME = "course.ai.plan.exchange";
    public static final String ROUTING_KEY = "course.ai.plan.key";

    @Bean
    public Queue aiCoursePlanQueue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }

    @Bean
    public DirectExchange aiCoursePlanExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding aiCoursePlanBinding(Queue aiCoursePlanQueue, DirectExchange aiCoursePlanExchange) {
        return BindingBuilder.bind(aiCoursePlanQueue).to(aiCoursePlanExchange).with(ROUTING_KEY);
    }
}
