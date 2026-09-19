package com.project.picngo.spot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PetTourRabbitMQConfig {

    public static final String QUEUE_NAME = "pettour.sync.queue";
    public static final String EXCHANGE_NAME = "pettour.sync.exchange";
    public static final String ROUTING_KEY = "pettour.sync.key";
    public static final String DEAD_LETTER_QUEUE_NAME = "pettour.sync.dlq";
    public static final String DEAD_LETTER_EXCHANGE_NAME = "pettour.sync.dlx";
    public static final String DEAD_LETTER_ROUTING_KEY = "pettour.sync.dead";

    @Bean
    public Queue petTourSyncQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE_NAME)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange petTourSyncExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding petTourSyncBinding(Queue petTourSyncQueue, DirectExchange petTourSyncExchange) {
        return BindingBuilder.bind(petTourSyncQueue).to(petTourSyncExchange).with(ROUTING_KEY);
    }

    @Bean
    public Queue petTourSyncDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build();
    }

    @Bean
    public DirectExchange petTourSyncDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE_NAME);
    }

    @Bean
    public Binding petTourSyncDeadLetterBinding(
            Queue petTourSyncDeadLetterQueue,
            DirectExchange petTourSyncDeadLetterExchange
    ) {
        return BindingBuilder.bind(petTourSyncDeadLetterQueue)
                .to(petTourSyncDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }
}
