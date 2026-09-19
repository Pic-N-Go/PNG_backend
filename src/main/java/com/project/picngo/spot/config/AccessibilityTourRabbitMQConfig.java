package com.project.picngo.spot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccessibilityTourRabbitMQConfig {

    public static final String QUEUE = "accessibilitytour.sync.queue";
    public static final String EXCHANGE = "accessibilitytour.sync.exchange";
    public static final String KEY = "accessibilitytour.sync.key";
    public static final String DLQ = "accessibilitytour.sync.dlq";
    public static final String DLX = "accessibilitytour.sync.dlx";
    public static final String DEAD_KEY = "accessibilitytour.sync.dead";

    @Bean
    Queue accessibilityTourQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DEAD_KEY)
                .build();
    }

    @Bean
    DirectExchange accessibilityTourExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    Binding accessibilityTourBinding(
            Queue accessibilityTourQueue,
            DirectExchange accessibilityTourExchange
    ) {
        return BindingBuilder.bind(accessibilityTourQueue)
                .to(accessibilityTourExchange)
                .with(KEY);
    }

    @Bean
    Queue accessibilityTourDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    DirectExchange accessibilityTourDlx() {
        return new DirectExchange(DLX);
    }

    @Bean
    Binding accessibilityTourDeadBinding(
            Queue accessibilityTourDlq,
            DirectExchange accessibilityTourDlx
    ) {
        return BindingBuilder.bind(accessibilityTourDlq)
                .to(accessibilityTourDlx)
                .with(DEAD_KEY);
    }
}
