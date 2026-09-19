package com.project.picngo.spot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PhotoAwardRabbitMQConfig {

    public static final String QUEUE_NAME = "photoaward.sync.queue";
    public static final String EXCHANGE_NAME = "photoaward.sync.exchange";
    public static final String ROUTING_KEY = "photoaward.sync.key";

    @Bean
    public Queue photoAwardSyncQueue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }

    @Bean
    public DirectExchange photoAwardSyncExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding photoAwardSyncBinding(Queue photoAwardSyncQueue, DirectExchange photoAwardSyncExchange) {
        return BindingBuilder.bind(photoAwardSyncQueue).to(photoAwardSyncExchange).with(ROUTING_KEY);
    }
}
