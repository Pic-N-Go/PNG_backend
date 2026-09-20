package com.project.picngo.spot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TourApiRabbitMQConfig {

    public static final String QUEUE_NAME = "tourapi.sync.v2.queue";
    public static final String EXCHANGE_NAME = "tourapi.sync.v2.exchange";
    public static final String ROUTING_KEY = "tourapi.sync.v2.key";
    public static final String DEAD_LETTER_QUEUE_NAME = "tourapi.sync.v2.dlq";
    public static final String DEAD_LETTER_STATUS_QUEUE_NAME = "tourapi.sync.v2.dlq.status";
    public static final String DEAD_LETTER_EXCHANGE_NAME = "tourapi.sync.v2.dlx";
    public static final String DEAD_LETTER_ROUTING_KEY = "tourapi.sync.v2.dead";

    @Bean
    public Queue tourApiSyncQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE_NAME)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange tourApiSyncExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding tourApiSyncBinding(Queue tourApiSyncQueue, DirectExchange tourApiSyncExchange) {
        return BindingBuilder.bind(tourApiSyncQueue).to(tourApiSyncExchange).with(ROUTING_KEY);
    }

    @Bean
    public Queue tourApiSyncDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build();
    }

    @Bean
    public Queue tourApiSyncDeadLetterStatusQueue() {
        return QueueBuilder.durable(DEAD_LETTER_STATUS_QUEUE_NAME).build();
    }

    @Bean
    public DirectExchange tourApiSyncDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE_NAME);
    }

    @Bean
    public Binding tourApiSyncDeadLetterBinding(
            Queue tourApiSyncDeadLetterQueue,
            DirectExchange tourApiSyncDeadLetterExchange
    ) {
        return BindingBuilder.bind(tourApiSyncDeadLetterQueue)
                .to(tourApiSyncDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Binding tourApiSyncDeadLetterStatusBinding(
            Queue tourApiSyncDeadLetterStatusQueue,
            DirectExchange tourApiSyncDeadLetterExchange
    ) {
        return BindingBuilder.bind(tourApiSyncDeadLetterStatusQueue)
                .to(tourApiSyncDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }
}
