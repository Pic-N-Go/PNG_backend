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

    public static final String QUEUE_NAME = "tourapi.sync.queue";
    public static final String EXCHANGE_NAME = "tourapi.sync.exchange";
    public static final String ROUTING_KEY = "tourapi.sync.key";

    @Bean
    public Queue tourApiSyncQueue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }

    @Bean
    public DirectExchange tourApiSyncExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding tourApiSyncBinding(Queue tourApiSyncQueue, DirectExchange tourApiSyncExchange) {
        return BindingBuilder.bind(tourApiSyncQueue).to(tourApiSyncExchange).with(ROUTING_KEY);
    }
}
