package com.project.picngo.notification.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String content,
        Boolean isRead,
        String deepLink,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(com.project.picngo.notification.domain.Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getIsRead(),
                notification.getDeepLink(),
                notification.getCreatedAt()
        );
    }
}
