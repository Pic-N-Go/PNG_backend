package com.project.picngo.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(indexes = @Index(name = "idx_notification_created_at", columnList = "createdAt"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String type; // e.g. GOLDEN_HOUR, WISHLIST

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Boolean isRead = false;

    private String deepLink;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Notification(Long userId, String type, String title, String content, String deepLink) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.deepLink = deepLink;
        this.isRead = false;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}



