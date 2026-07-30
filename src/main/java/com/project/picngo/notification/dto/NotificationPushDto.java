package com.project.picngo.notification.dto;

import java.io.Serializable;

/**
 * RabbitMQ 큐에 던질 데이터의 형태
 */
public record NotificationPushDto(
        Long userId,
        String type,
        String title,
        String content,
        String deepLink,
        Long spotId,
        String dedupeKey
) implements Serializable {}
